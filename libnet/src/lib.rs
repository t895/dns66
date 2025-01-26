use std::{
    collections::HashSet,
    fs::File,
    hash::{DefaultHasher, Hash, Hasher},
    io::{self, BufRead, Read, Write},
    net::{IpAddr, Ipv4Addr, Ipv6Addr, SocketAddrV6, UdpSocket},
    os::fd::{AsRawFd, FromRawFd},
    str::FromStr,
    sync::Arc,
    thread,
    time::{self, SystemTime, UNIX_EPOCH},
};

use android_logger::Config;
use etherparse::{
    ip_number, IpHeaders, IpNumber, Ipv4Header, Ipv6FlowLabel, Ipv6Header, NetSlice, PacketBuilder,
    PacketBuilderStep, SlicedPacket, TransportSlice, UdpSlice,
};
use libc::pollfd;
use linux_syscall::{syscall, Result};
use simple_dns::{rdata::RData, Name, PacketFlag, ResourceRecord};
use uniffi::deps::log::LevelFilter;

#[macro_use]
extern crate log;
extern crate android_logger;

uniffi::setup_scaffolding!();

#[uniffi::export]
pub fn rust_init() {
    android_logger::init_once(
        Config::default()
            .with_max_level(LevelFilter::Trace) // limit log level
            .with_tag("DNSNet Native"), // logs will show under mytag tag
    );
}

#[uniffi::export]
pub fn native_pipe() -> Option<Vec<i32>> {
    let pipes = vec![0, 0];
    let result = unsafe { syscall!(linux_syscall::SYS_pipe2, pipes.as_ptr() as usize, 0) }.check();
    return match result {
        Ok(_) => Some(pipes),
        Err(_) => None,
    };
}

#[uniffi::export]
pub fn native_close(fd: i32) -> i32 {
    let _ = unsafe { syscall!(linux_syscall::SYS_close, fd) }.check();
    return -1;
}

#[uniffi::export]
pub fn run_vpn_native(
    ad_vpn_callback: Box<dyn AdVpnCallback>,
    block_logger_callback: Box<dyn BlockLoggerCallback>,
    host_items: Vec<NativeHost>,
    host_exceptions: Vec<NativeHost>,
    upstream_dns_servers: Vec<Vec<u8>>,
    watchdog_target_address: String,
    vpn_fd: i32,
    block_fd: i32,
    watchdog_enabled: bool,
) {
    let mut vpn = AdVpn::new(vpn_fd, block_fd, watchdog_enabled, &watchdog_target_address);
    vpn.run(
        ad_vpn_callback,
        block_logger_callback,
        host_items,
        host_exceptions,
        upstream_dns_servers,
    );
}

fn build_packet_v4(
    source_address: &[u8; 4],
    source_port: u16,
    destination_address: &[u8; 4],
    destination_port: u16,
    time_to_live: u8,
    identification: u16,
    response_payload: &[u8],
) -> Option<Vec<u8>> {
    let mut header = match Ipv4Header::new(
        response_payload.len() as u16,
        time_to_live,
        ip_number::UDP,
        *source_address,
        *destination_address,
    ) {
        Ok(value) => {
            debug!("build_packet_v4: Successfully created Ipv4Header");
            value
        }
        Err(e) => {
            error!(
                "build_packet_v4: Failed to create Ipv4Header! - {}",
                e.to_string()
            );
            return None;
        }
    };

    header.identification = identification;
    let builder = PacketBuilder::ip(IpHeaders::Ipv4(header, Default::default()));
    return build_packet(builder, source_port, destination_port, response_payload);
}

fn build_packet_v6(
    source_address: &[u8; 16],
    source_port: u16,
    destination_address: &[u8; 16],
    destination_port: u16,
    traffic_class: u8,
    flow_label: Ipv6FlowLabel,
    hop_limit: u8,
    response_payload: &[u8],
) -> Option<Vec<u8>> {
    let header = Ipv6Header {
        traffic_class,
        flow_label,
        payload_length: response_payload.len() as u16,
        next_header: IpNumber::UDP,
        hop_limit,
        source: *source_address,
        destination: *destination_address,
    };
    let builder = PacketBuilder::ip(IpHeaders::Ipv6(header, Default::default()));
    return build_packet(builder, source_port, destination_port, response_payload);
}

fn build_packet(
    builder: PacketBuilderStep<IpHeaders>,
    source_port: u16,
    destination_port: u16,
    response_payload: &[u8],
) -> Option<Vec<u8>> {
    let udp_builder = builder.udp(source_port, destination_port);
    let mut result = Vec::<u8>::with_capacity(udp_builder.size(response_payload.len()));
    match udp_builder.write(&mut result, &response_payload) {
        Ok(_) => debug!("build_packet: Successfully built packet"),
        Err(e) => {
            error!("build_packet: Failed to build packet! - {}", e.to_string());
            return None;
        }
    };
    return Some(result);
}

#[derive(Debug)]
struct GenericIpPacket<'a> {
    packet: SlicedPacket<'a>,
}

impl<'a> GenericIpPacket<'a> {
    fn from_array(data: &'a [u8]) -> Option<Self> {
        match SlicedPacket::from_ip(data) {
            Ok(value) => Some(GenericIpPacket::new(value)),
            Err(_) => None,
        }
    }

    fn new(packet: SlicedPacket<'a>) -> Self {
        Self { packet }
    }

    fn get_ipv4_header(&self) -> Option<Ipv4Header> {
        match &self.packet.net {
            Some(net) => match net {
                NetSlice::Ipv4(value) => Some(value.header().to_header()),
                NetSlice::Ipv6(_) => None,
            },
            None => None,
        }
    }

    fn get_ipv6_header(&self) -> Option<Ipv6Header> {
        match &self.packet.net {
            Some(net) => match net {
                NetSlice::Ipv4(_) => None,
                NetSlice::Ipv6(value) => Some(value.header().to_header()),
            },
            None => None,
        }
    }

    fn get_destination_address(&self) -> Option<Vec<u8>> {
        let ipv4_header = self.get_ipv4_header();
        if ipv4_header.is_some() {
            return Some(ipv4_header.unwrap().destination.to_vec());
        }

        let ipv6_header = self.get_ipv6_header();
        if ipv6_header.is_some() {
            return Some(ipv6_header.unwrap().destination.to_vec());
        }

        return None;
    }

    pub fn get_udp_packet(&self) -> Option<&UdpSlice> {
        match &self.packet.transport {
            Some(transport) => match transport {
                TransportSlice::Udp(udp) => Some(udp),
                _ => None,
            },
            None => None,
        }
    }
}

fn build_response_packet(request_packet: &[u8], response_payload: Vec<u8>) -> Option<Vec<u8>> {
    let generic_request_packet = match GenericIpPacket::from_array(request_packet) {
        Some(value) => value,
        None => return None,
    };

    let request_payload = match generic_request_packet.get_udp_packet() {
        Some(value) => value,
        None => return None,
    };

    match generic_request_packet.get_ipv4_header() {
        Some(header) => {
            return build_packet_v4(
                &header.destination,
                request_payload.destination_port(),
                &header.source,
                request_payload.source_port(),
                header.time_to_live,
                header.identification,
                &response_payload,
            );
        }
        None => {}
    };

    match generic_request_packet.get_ipv6_header() {
        Some(header) => {
            return build_packet_v6(
                &header.destination,
                request_payload.destination_port(),
                &header.source,
                request_payload.source_port(),
                header.traffic_class,
                header.flow_label,
                header.hop_limit,
                &response_payload,
            );
        }
        None => {}
    }

    return None;
}

fn get_epoch_millis() -> u128 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap()
        .as_millis()
}

pub enum VpnStatus {
    Starting,
    Running,
    Stopping,
    WaitingForNetwork,
    Reconnecting,
    ReconnectingNetworkError,
    Stopped,
}

impl VpnStatus {
    fn ordinal(&self) -> i32 {
        match self {
            VpnStatus::Starting => 0,
            VpnStatus::Running => 1,
            VpnStatus::Stopping => 2,
            VpnStatus::WaitingForNetwork => 3,
            VpnStatus::Reconnecting => 4,
            VpnStatus::ReconnectingNetworkError => 5,
            VpnStatus::Stopped => 6,
        }
    }
}

#[uniffi::export(callback_interface)]
pub trait AdVpnCallback: Send + Sync {
    fn protect_raw_socket_fd(&self, socket_fd: i32);

    fn notify(&self, native_status: i32);
}

struct AdVpn {
    vpn_file: File,
    block_fd: i32,
    device_writes: Vec<Vec<u8>>,
    vpn_watchdog: VpnWatchdog,
    wosp_list: WospList,
    ipv6_wildcard: SocketAddrV6,
}

impl AdVpn {
    const DNS_RESPONSE_PACKET_SIZE: usize = 1024;

    const DNS_PORT: u16 = 53;

    fn new(
        vpn_fd: i32,
        block_fd: i32,
        watchdog_enabled: bool,
        watchdog_target_address: &str,
    ) -> Self {
        // SAFETY: Please
        let vpn_file = unsafe { File::from_raw_fd(vpn_fd) };

        // TODO: Add more robust address check
        let target_address: IpAddr = if watchdog_target_address.contains(".") {
            let ipv4addr = match Ipv4Addr::from_str(watchdog_target_address) {
                Ok(value) => value,
                Err(e) => {
                    error!(
                        "new: Failed to create watchdog target IPv4 address from string! - {} : {}",
                        watchdog_target_address,
                        e.to_string()
                    );
                    panic!();
                }
            };
            std::net::IpAddr::V4(ipv4addr)
        } else if watchdog_target_address.contains(":") {
            let ipv6addr = match Ipv6Addr::from_str(watchdog_target_address) {
                Ok(value) => value,
                Err(e) => {
                    error!(
                        "new: Failed to create watchdog target IPv6 address from string! - {} : {}",
                        watchdog_target_address,
                        e.to_string()
                    );
                    panic!();
                }
            };
            std::net::IpAddr::V6(ipv6addr)
        } else {
            error!(
                "new: Failed to create watchdog target address from string! - {}",
                watchdog_target_address
            );
            panic!()
        };

        AdVpn {
            vpn_file,
            block_fd,
            device_writes: Vec::new(),
            vpn_watchdog: VpnWatchdog::new(watchdog_enabled, (target_address, Self::DNS_PORT)),
            wosp_list: WospList::new(),
            ipv6_wildcard: SocketAddrV6::new(Ipv6Addr::new(0, 0, 0, 0, 0, 0, 0, 0), 0, 0, 0),
        }
    }

    fn run(
        &mut self,
        android_vpn_callback: Box<dyn AdVpnCallback>,
        block_logger_callback: Box<dyn BlockLoggerCallback>,
        host_items: Vec<NativeHost>,
        host_exceptions: Vec<NativeHost>,
        upstream_dns_servers: Vec<Vec<u8>>,
    ) {
        let mut packet: Vec<u8> = Vec::with_capacity(32767);
        let mut dns_packet_proxy = DnsPacketProxy::new(android_vpn_callback, block_logger_callback);
        dns_packet_proxy.initialize(host_items, host_exceptions, upstream_dns_servers);
        self.vpn_watchdog.init();
        while self.do_one(&mut dns_packet_proxy, packet.as_mut_slice()) {}
    }

    fn do_one(&mut self, dns_packet_proxy: &mut DnsPacketProxy, packet: &mut [u8]) -> bool {
        let mut device_poll_fd = pollfd {
            fd: self.vpn_file.as_raw_fd(),
            events: libc::POLLIN,
            revents: 0,
        };
        let block_poll_fd = pollfd {
            fd: self.block_fd,
            events: libc::POLLHUP | libc::POLLERR,
            revents: 0,
        };

        if !self.device_writes.is_empty() {
            device_poll_fd.events = device_poll_fd.events | libc::POLLOUT;
        }

        let length = self.wosp_list.list.len();
        let mut polls: Vec<pollfd> = Vec::with_capacity(2 + length);
        polls.push(device_poll_fd);
        polls.push(block_poll_fd);
        for i in 0..length {
            let wosp = match self.wosp_list.list.get(i) {
                Some(value) => value,
                None => {
                    error!("do_one: Used bad index for WOSP list!");
                    return false;
                }
            };
            let poll_fd = pollfd {
                fd: wosp.socket.as_raw_fd(),
                events: libc::POLLIN,
                revents: 0,
            };
            polls.push(poll_fd);
        }

        if block_poll_fd.revents != 0 {
            info!("do_one: Told to stop VPN");
            return false;
        }

        debug!("do_one: Polling {} file descriptors", polls.len());
        let result = unsafe {
            syscall!(
                linux_syscall::SYS_ppoll,
                polls.as_mut_ptr(),
                polls.len(),
                self.vpn_watchdog.get_poll_timeout()
            )
        };

        match result.check() {
            Ok(_) => debug!("do_one: ppoll ran successfully"),
            Err(e) => {
                error!("do_one: ppoll failed! - Error code: {}", e.get());
                return false;
            }
        }

        if result.as_usize_unchecked() == 0 {
            self.vpn_watchdog.handle_timeout();
            return true;
        }

        // Need to do this before reading from the device, otherwise a new insertion there could
        // invalidate one of the sockets we want to read from either due to size or time out
        // constraints
        // TODO: Calm down with the raw indices
        if !self.wosp_list.list.is_empty() {
            let mut wosp_index: usize = 0;
            let mut polls_index: i32 = -1;
            let mut wosps_to_process = Vec::<WaitingOnSocketPacket>::new();
            while wosp_index < self.wosp_list.list.len() {
                polls_index += 1;
                let poll = match polls.get((polls_index as usize) + 2) {
                    Some(value) => value,
                    None => {
                        error!("do_one: Used bad index for polls list!");
                        return false;
                    }
                };

                if poll.revents & libc::POLLIN != 0 {
                    let wosp = self.wosp_list.list.remove(wosp_index);
                    debug!("do_one: Read from DNS socket: {:?}", wosp.socket);
                    wosps_to_process.push(wosp);
                    wosp_index = (wosp_index - 1).clamp(0, self.wosp_list.list.len());
                    continue;
                }
                wosp_index += 1;
            }

            for wosp in wosps_to_process {
                self.handle_raw_dns_response(dns_packet_proxy, &wosp.packet, &wosp.socket);
            }
        }

        if device_poll_fd.revents & libc::POLLOUT != 0 {
            debug!("do_one: Write to device");
            let success = self.write_to_device();
            if !success {
                return false;
            }
        }

        if device_poll_fd.revents & libc::POLLIN != 0 {
            debug!("do_one: Read from device");
            let success = self.read_packet_from_device(dns_packet_proxy, packet);
            if !success {
                return false;
            }
        }

        return true;
    }

    fn write_to_device(&mut self) -> bool {
        let device_write = match self.device_writes.first() {
            Some(value) => value,
            None => {
                error!("write_to_device: device_writes is empty! This should be impossible");
                return false;
            }
        };

        match self.vpn_file.write(&device_write) {
            Ok(_) => true,
            Err(e) => {
                error!("write_to_device: Failed writing - {}", e.to_string());
                false
            }
        }
    }

    fn read_packet_from_device(
        &mut self,
        dns_packet_proxy: &mut DnsPacketProxy,
        packet: &mut [u8],
    ) -> bool {
        let length = match self.vpn_file.read(packet) {
            Ok(value) => value,
            Err(e) => {
                error!(
                    "read_packet_from_device: Cannot read from device - {}",
                    e.to_string()
                );
                return false;
            }
        };

        if length == 0 {
            warn!("read_packet_from_device: Got empty packet!");
            return true;
        }

        self.vpn_watchdog.handle_packet(packet);
        dns_packet_proxy.handle_dns_request(self, packet);

        return true;
    }

    fn handle_raw_dns_response(
        &mut self,
        dns_packet_proxy: &DnsPacketProxy,
        parsed_packet: &Vec<u8>,
        dns_socket: &UdpSocket,
    ) {
        let mut reply_packet: Vec<u8> = Vec::with_capacity(Self::DNS_RESPONSE_PACKET_SIZE);

        let recv_result = dns_socket.recv_from(reply_packet.as_mut_slice());
        if recv_result.is_err() {
            error!(
                "handle_raw_dns_response: Failed to receive response packet from DNS socket! - {}",
                recv_result.unwrap_err().to_string()
            );
            return;
        }

        dns_packet_proxy.handle_dns_response(self, parsed_packet, reply_packet);
    }

    fn forward_packet(
        &mut self,
        android_vpn_service: &Box<dyn AdVpnCallback>,
        packet: Vec<u8>,
        request_packet: &[u8],
    ) -> bool {
        let socket = match UdpSocket::bind(&self.ipv6_wildcard) {
            Ok(value) => value,
            Err(e) => {
                error!("forward_packet: Failed to bind socket! - {}", e.to_string());
                let os_error = e.raw_os_error();
                if os_error.is_some() {
                    return Self::eval_socket_error(os_error.unwrap());
                }
                return false;
            }
        };

        // Packets to be sent to the real DNS server will need to be protected from the VPN
        android_vpn_service.protect_raw_socket_fd(socket.as_raw_fd());

        match socket.send(packet.as_slice()) {
            Ok(_) => return true,
            Err(e) => {
                error!(
                    "forward_packet: Failed to send message! - {}",
                    e.to_string()
                );
                if e.raw_os_error().is_some() {
                    return Self::eval_socket_error(e.raw_os_error().unwrap());
                }
            }
        }

        self.wosp_list
            .add(WaitingOnSocketPacket::new(socket, request_packet.to_vec()));

        return true;
    }

    fn eval_socket_error(error_code: i32) -> bool {
        error!("eval_socket_error: Cannot send message");
        return error_code != libc::ENETUNREACH || error_code != libc::EPERM;
    }

    fn queue_device_write(&mut self, packet: Vec<u8>) {
        self.device_writes.push(packet)
    }
}

struct VpnWatchdog {
    enabled: bool,
    init_penalty: u64,
    last_packet_sent: u128,
    last_packet_received: u128,
    poll_timeout: i32,
    target_address: (IpAddr, u16),
}

impl VpnWatchdog {
    // Polling is quadrupled on every success, and values range from 4s to 1h8m.
    const POLL_TIMEOUT_START: i32 = 1000;
    const POLL_TIMEOUT_END: i32 = 4096000;
    const POLL_TIMEOUT_WAITING: i32 = 7000;
    const POLL_TIMEOUT_GROW: i32 = 4;

    // Reconnect penalty ranges from 0s to 5s, in increments of 200 ms.
    const INIT_PENALTY_START: u64 = 0;
    const INIT_PENALTY_END: u64 = 5000;
    const INIT_PENALTY_INC: u64 = 200;

    fn new(enabled: bool, target_address: (IpAddr, u16)) -> Self {
        Self {
            enabled,
            init_penalty: Self::INIT_PENALTY_START,
            last_packet_sent: 0,
            last_packet_received: 0,
            poll_timeout: VpnWatchdog::POLL_TIMEOUT_START,
            target_address,
        }
    }

    fn init(&self) {
        if self.init_penalty > 0 {
            thread::sleep(time::Duration::from_millis(self.init_penalty));
        }
    }

    fn get_poll_timeout(&self) -> i32 {
        return if !self.enabled {
            -1
        } else if self.last_packet_received < self.last_packet_sent {
            VpnWatchdog::POLL_TIMEOUT_WAITING
        } else {
            self.poll_timeout
        };
    }

    fn handle_packet(&mut self, packet_data: &[u8]) {
        if !self.enabled {
            return;
        }

        debug!(
            "handle_packet: Received packet of length {}",
            packet_data.len()
        );
        self.last_packet_received = get_epoch_millis();
    }

    fn send_packet(&mut self) {
        if !self.enabled {
            return;
        }

        debug!(
            "send_packet: Sending packet, poll timeout is {}",
            self.poll_timeout
        );
        let result = UdpSocket::bind("[::]:53");
        match result {
            Ok(socket) => {
                match socket.send_to(vec![].as_slice(), &self.target_address) {
                    Ok(_) => debug!("send_packet: Successfully sent packet over UDP socket"),
                    Err(e) => {
                        error!(
                            "send_packet: Failed to send packet over UDP socket! - {}",
                            e.to_string()
                        );
                        return;
                    }
                };
                self.last_packet_sent = get_epoch_millis();
            }
            Err(e) => error!(
                "send_packet: Failed to send watchdog packet! - {}",
                e.to_string()
            ),
        };
    }

    fn handle_timeout(&mut self) -> bool {
        if !self.enabled {
            return false;
        }

        debug!(
            "handleTimeout: Milliseconds elapsed between last receive and sent: {}",
            self.last_packet_received
        );

        if self.last_packet_received < self.last_packet_sent && self.last_packet_sent != 0 {
            self.init_penalty += Self::INIT_PENALTY_INC;
            if self.init_penalty > Self::INIT_PENALTY_END {
                self.init_penalty = Self::INIT_PENALTY_END;
            }
            return true;
        }

        self.poll_timeout *= Self::POLL_TIMEOUT_GROW;
        if self.poll_timeout > Self::POLL_TIMEOUT_END {
            self.poll_timeout = Self::POLL_TIMEOUT_END;
        }

        self.send_packet();
        return false;
    }
}

struct WaitingOnSocketPacket {
    socket: UdpSocket,
    packet: Vec<u8>,
    time: u128,
}

impl WaitingOnSocketPacket {
    fn new(socket: UdpSocket, packet: Vec<u8>) -> Self {
        Self {
            socket,
            packet,
            time: get_epoch_millis(),
        }
    }

    fn age_seconds(&self) -> u128 {
        (get_epoch_millis() - self.time) / 1000
    }
}

struct WospList {
    list: Vec<WaitingOnSocketPacket>,
}

impl WospList {
    const DNS_MAXIMUM_WAITING: usize = 1024;
    const DNS_TIMEOUT_SEC: u128 = 10;

    fn new() -> Self {
        Self { list: Vec::new() }
    }

    fn add(&mut self, wosp: WaitingOnSocketPacket) {
        if self.list.len() > Self::DNS_MAXIMUM_WAITING {
            debug!(
                "add: Dropping socket due to space constraints: {:?}",
                self.list.first().unwrap().packet
            );
            self.list.remove(0);
        }

        while !self.list.is_empty()
            && self.list.first().unwrap().age_seconds() > Self::DNS_TIMEOUT_SEC
        {
            debug!(
                "add: Timeout on socket {:?}",
                self.list.first().unwrap().socket
            );
            self.list.remove(0);
        }

        self.list.push(wosp);
    }
}

#[derive(uniffi::Enum, PartialEq, PartialOrd)]
pub enum NativeHostState {
    IGNORE,
    DENY,
    ALLOW,
}

#[derive(uniffi::Record)]
pub struct NativeHost {
    title: String,
    data: String,
    state: NativeHostState,
}

#[derive(uniffi::Object)]
struct RuleDatabase {
    blocked_hosts: Arc<HashSet<u64>>,
}

impl RuleDatabase {
    const IPV4_LOOPBACK: &'static str = "127.0.0.1";
    const IPV6_LOOPBACK: &'static str = "::1";
    const NO_ROUTE: &'static str = "0.0.0.0";

    fn parse_line(line: &str) -> Option<String> {
        if line.trim().is_empty() {
            return None;
        }

        let mut end_of_line = match line.find('#') {
            Some(index) => index,
            None => line.len(),
        };

        let mut start_of_host = 0;

        match line.find(Self::IPV4_LOOPBACK) {
            Some(index) => {
                start_of_host += index + Self::IPV4_LOOPBACK.len();
            }
            None => {}
        };

        if start_of_host == 0 {
            match line.find(Self::IPV6_LOOPBACK) {
                Some(index) => {
                    start_of_host += index + Self::IPV6_LOOPBACK.len();
                }
                None => {}
            }
        }

        if start_of_host == 0 {
            match line.find(Self::NO_ROUTE) {
                Some(index) => {
                    start_of_host += index + Self::NO_ROUTE.len();
                }
                None => {}
            }
        }

        if start_of_host >= end_of_line {
            return None;
        }

        while start_of_host < end_of_line
            && line.chars().nth(start_of_host).unwrap().is_whitespace()
        {
            start_of_host += 1;
        }

        while start_of_host > end_of_line
            && line.chars().nth(end_of_line - 1).unwrap().is_whitespace()
        {
            end_of_line -= 1;
        }

        let host = (&line[start_of_host..end_of_line]).to_lowercase();
        if host.is_empty() || host.contains(char::is_whitespace) {
            return None;
        }

        return Some(host);
    }

    #[uniffi::constructor]
    fn new() -> Self {
        RuleDatabase {
            blocked_hosts: Arc::new(HashSet::new()),
        }
    }

    fn initialize(&mut self, host_items: Vec<NativeHost>, host_exceptions: Vec<NativeHost>) {
        info!("initialize: Loading block list");

        let mut new_set: HashSet<u64> = HashSet::new();

        let mut sorted_host_items = host_items
            .iter()
            .filter(|item| item.state != NativeHostState::IGNORE)
            .collect::<Vec<&NativeHost>>();
        sorted_host_items.sort_by(|a, b| a.state.partial_cmp(&b.state).unwrap());

        for item in sorted_host_items.iter() {
            self.load_item(&mut new_set, item);
        }

        let mut sorted_host_exceptions = host_exceptions
            .iter()
            .filter(|item| item.state != NativeHostState::IGNORE)
            .collect::<Vec<&NativeHost>>();
        sorted_host_exceptions.sort_by(|a, b| a.state.partial_cmp(&b.state).unwrap());

        for exception in sorted_host_exceptions {
            self.add_host(&mut new_set, &exception.state, &exception.data);
        }

        self.blocked_hosts = Arc::new(new_set);
    }

    fn load_item(&mut self, set: &mut HashSet<u64>, host: &NativeHost) {
        if host.state == NativeHostState::IGNORE {
            return;
        }

        match File::open(&host.data) {
            Ok(file) => {
                let lines: io::Lines<io::BufReader<File>> = io::BufReader::new(file).lines();
                self.load_file(set, &host, lines);
            }
            Err(_) => {
                self.add_host(set, &host.state, &host.data);
            }
        }
    }

    fn add_host<T: Hash>(&mut self, set: &mut HashSet<u64>, state: &NativeHostState, data: &T) {
        let mut hasher = DefaultHasher::new();
        data.hash(&mut hasher);
        let hash = hasher.finish();
        match state {
            NativeHostState::IGNORE => return,
            NativeHostState::DENY => set.insert(hash),
            NativeHostState::ALLOW => set.remove(&hash),
        };
    }

    fn load_file(
        &mut self,
        set: &mut HashSet<u64>,
        host: &NativeHost,
        lines: io::Lines<io::BufReader<File>>,
    ) {
        let mut count = 0;
        for line in lines {
            match line {
                Ok(value) => {
                    let data = Self::parse_line(value.as_str());
                    if data.is_some() {
                        self.add_host(set, &host.state, &data.unwrap());
                    }
                    count += 1;
                }
                Err(e) => {
                    error!(
                        "load_file: Error while reading {} after {} lines - {}",
                        &host.data,
                        count,
                        e.to_string()
                    );
                    return;
                }
            }
        }
        debug!("load_file: Loaded {} hosts from {}", count, &host.data);
    }

    fn is_blocked(&self, host: &str) -> bool {
        let mut hasher = DefaultHasher::new();
        host.hash(&mut hasher);
        self.blocked_hosts.contains(&hasher.finish())
    }
}

#[uniffi::export(callback_interface)]
pub trait BlockLoggerCallback: Send + Sync {
    fn log(&self, connection_name: String, allowed: bool);
}

struct DnsPacketProxy<'a> {
    android_vpn_callback: Box<dyn AdVpnCallback>,
    block_logger_callback: Box<dyn BlockLoggerCallback>,
    rule_database: RuleDatabase,
    upstream_dns_servers: Vec<Vec<u8>>,
    negative_cache_record: ResourceRecord<'a>,
}

impl<'a> DnsPacketProxy<'a> {
    const INVALID_HOSTNAME: &'static str = "dnsnet.dnsnet.invalid.";
    const NEGATIVE_CACHE_TTL_SECONDS: u32 = 5;

    fn new(
        android_vpn_callback: Box<dyn AdVpnCallback>,
        block_logger_callback: Box<dyn BlockLoggerCallback>,
    ) -> Self {
        let name = match Name::new(Self::INVALID_HOSTNAME) {
            Ok(value) => value,
            Err(e) => {
                error!(
                    "Failed to parse our invalid hostname! - {:?}",
                    e.to_string()
                );
                panic!();
            }
        };
        let soa_record = RData::SOA(simple_dns::rdata::SOA {
            mname: name.clone(),
            rname: name.clone(),
            serial: 0,
            refresh: 0,
            retry: 0,
            expire: 0,
            minimum: Self::NEGATIVE_CACHE_TTL_SECONDS,
        });
        let negative_cache_record = ResourceRecord::new(
            name,
            simple_dns::CLASS::IN,
            Self::NEGATIVE_CACHE_TTL_SECONDS,
            soa_record,
        );
        DnsPacketProxy {
            android_vpn_callback,
            block_logger_callback,
            rule_database: RuleDatabase::new(),
            upstream_dns_servers: Vec::new(),
            negative_cache_record,
        }
    }

    fn initialize(
        &mut self,
        host_items: Vec<NativeHost>,
        host_exceptions: Vec<NativeHost>,
        upstream_dns_servers: Vec<Vec<u8>>,
    ) {
        self.rule_database.initialize(host_items, host_exceptions);
        self.upstream_dns_servers = upstream_dns_servers;
    }

    fn handle_dns_response(
        &self,
        ad_vpn: &mut AdVpn,
        request_packet: &[u8],
        response_payload: Vec<u8>,
    ) {
        match build_response_packet(request_packet, response_payload) {
            Some(packet) => ad_vpn.queue_device_write(packet),
            None => return,
        };
    }

    fn handle_dns_request(&mut self, ad_vpn: &mut AdVpn, packet_data: &[u8]) {
        let packet = match GenericIpPacket::from_array(packet_data) {
            Some(value) => value,
            None => {
                warn!(
                    "handle_dns_request: Failed to parse packet data - {:?}",
                    packet_data
                );
                return;
            }
        };

        // TODO: We currently assume that DNS requests will be UDP only. This is not true.
        let udp_packet = match packet.get_udp_packet() {
            Some(value) => value,
            None => {
                warn!(
                    "handle_dns_request: Failed to get UDP packet from generic packet - {:?}",
                    packet
                );
                return;
            }
        };

        let destination_address = match packet.get_destination_address() {
            Some(value) => value,
            None => {
                warn!(
                    "handle_dns_request: Failed to get destination address for packet - {:?}",
                    packet
                );
                return;
            }
        };
        let real_destination_address =
            match self.translate_destination_address(&destination_address) {
                Some(value) => value,
                None => {
                    warn!(
                        "handle_dns_request: Failed to translate destination address - {:?}",
                        destination_address
                    );
                    return;
                }
            };

        let destination_port = udp_packet.destination_port();
        let mut dns_packet = match simple_dns::Packet::parse(udp_packet.payload()) {
            Ok(value) => value,
            Err(e) => {
                warn!(
                    "handle_dns_request: Discarding no-DNS or invalid packet - {}",
                    e.to_string()
                );
                return;
            }
        };

        if dns_packet.questions.is_empty() {
            warn!(
                "handle_dns_request: Discarding DNS packet with no query - {:?}",
                dns_packet
            );
            return;
        }

        // TODO: Do this without allocating an extra string
        let dns_query_name = dns_packet
            .questions
            .first()
            .unwrap()
            .qname
            .to_string()
            .to_lowercase();
        if self.rule_database.is_blocked(&dns_query_name) {
            info!(
                "handle_dns_request: DNS Name {} allowed. Sending to {:?}",
                dns_query_name, real_destination_address
            );
            self.block_logger_callback.log(dns_query_name, true);

            if real_destination_address.len() == 4 {
                // IPV4
                let packet_header = match packet.get_ipv4_header() {
                    Some(value) => value,
                    None => {
                        warn!("handle_dns_request: Failed to get IPV4 header from DNS request packet - {:?}", packet);
                        return;
                    }
                };

                let builder = PacketBuilder::ipv4(
                    packet_header.source,
                    real_destination_address.try_into().unwrap(),
                    packet_header.time_to_live,
                )
                .udp(udp_packet.source_port(), destination_port);
                let mut out_packet =
                    Vec::<u8>::with_capacity(builder.size(udp_packet.payload().len()));

                match builder.write(&mut out_packet, &udp_packet.payload()) {
                    Ok(_) => {}
                    Err(_) => {
                        warn!("handle_dns_request: Failed to write IPV6 response packet!");
                        return;
                    }
                };

                ad_vpn.forward_packet(&self.android_vpn_callback, out_packet, packet_data);
            } else if real_destination_address.len() == 16 {
                // IPV6
                let packet_header = match packet.get_ipv6_header() {
                    Some(value) => value,
                    None => {
                        warn!("handle_dns_request: Failed to get IPV4 header from DNS request packet - {:?}", packet);
                        return;
                    }
                };

                let builder = PacketBuilder::ipv6(
                    packet_header.source,
                    real_destination_address.try_into().unwrap(),
                    packet_header.hop_limit,
                )
                .udp(udp_packet.source_port(), destination_port);
                let mut out_packet =
                    Vec::<u8>::with_capacity(builder.size(udp_packet.payload().len()));

                match builder.write(&mut out_packet, &udp_packet.payload()) {
                    Ok(_) => {}
                    Err(_) => {
                        warn!("handle_dns_request: Failed to write IPV6 response packet!");
                        return;
                    }
                };

                ad_vpn.forward_packet(&self.android_vpn_callback, out_packet, packet_data);
            } else {
                warn!("handle_dns_request: Received destination address from unknown protocol! - {:?}", real_destination_address);
            }
        } else {
            info!("handle_dns_request: DNS Name {} blocked!", dns_query_name);
            self.block_logger_callback.log(dns_query_name, false);

            dns_packet.set_flags(PacketFlag::RESPONSE);
            *dns_packet.rcode_mut() = simple_dns::RCODE::NoError;
            dns_packet
                .additional_records
                .push(self.negative_cache_record.clone());

            let mut wire = Vec::<u8>::new();
            match dns_packet.write_to(&mut wire) {
                Ok(_) => debug!("Packet written to wire successfully!"),
                Err(e) => {
                    error!("Failed to write DNS packet to wire! - {}", e.to_string());
                    return;
                }
            };

            self.handle_dns_response(ad_vpn, packet_data, wire);
        }
    }

    fn translate_destination_address(&self, destination_address: &Vec<u8>) -> Option<Vec<u8>> {
        return if !self.upstream_dns_servers.is_empty() {
            let index = match destination_address.get(destination_address.len() - 1) {
                Some(value) => value,
                None => return None,
            };

            self.upstream_dns_servers.get(*index as usize).cloned()
        } else {
            Some(destination_address.clone())
        };
    }
}
