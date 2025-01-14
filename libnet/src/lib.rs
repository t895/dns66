use std::{
    collections::HashSet,
    fs::File,
    hash::{DefaultHasher, Hash, Hasher},
    io::{self, BufRead},
    net::UdpSocket,
    os::fd::AsRawFd,
    sync::Arc,
    thread,
    time::{self, SystemTime, UNIX_EPOCH},
};

use android_logger::Config;
use dns_parser::ResponseCode;
use etherparse::{
    ip_number, IpHeaders, IpNumber, Ipv4Header, Ipv6FlowLabel,
    Ipv6Header, NetSlice, PacketBuilder, PacketBuilderStep,
    SlicedPacket, TransportSlice, UdpSlice,
};
use libc::pollfd;
use linux_syscall::{syscall, Result, ResultSize};
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

pub fn build_packet_v4(
    source_address: &[u8; 4],
    source_port: u16,
    destination_address: &[u8; 4],
    destination_port: u16,
    time_to_live: u8,
    identification: u16,
    response_payload: &[u8],
) -> Vec<u8> {
    let mut header = Ipv4Header::new(
        response_payload.len() as u16,
        time_to_live,
        ip_number::UDP,
        source_address[0..4].try_into().unwrap(),
        destination_address[0..4].try_into().unwrap(),
    )
    .unwrap();
    header.identification = identification;
    let builder = PacketBuilder::ip(IpHeaders::Ipv4(header, Default::default()));
    return build_packet(builder, source_port, destination_port, response_payload);
}

pub fn build_packet_v6(
    source_address: &[u8; 16],
    source_port: u16,
    destination_address: &[u8; 16],
    destination_port: u16,
    traffic_class: u8,
    flow_label: Ipv6FlowLabel,
    hop_limit: u8,
    response_payload: &[u8],
) -> Vec<u8> {
    let header = Ipv6Header {
        traffic_class,
        flow_label,
        payload_length: response_payload.len() as u16,
        next_header: IpNumber::UDP,
        hop_limit,
        source: source_address[0..16].try_into().unwrap(),
        destination: destination_address[0..16].try_into().unwrap(),
    };
    let builder = PacketBuilder::ip(IpHeaders::Ipv6(header, Default::default()));
    return build_packet(builder, source_port, destination_port, response_payload);
}

fn build_packet(
    builder: PacketBuilderStep<IpHeaders>,
    source_port: u16,
    destination_port: u16,
    response_payload: &[u8],
) -> Vec<u8> {
    let udp_builder = builder.udp(source_port, destination_port);
    let mut result = Vec::<u8>::with_capacity(udp_builder.size(response_payload.len()));
    udp_builder.write(&mut result, &response_payload).unwrap();
    return result;
}

#[derive(Debug)]
struct GenericIpPacket<'a> {
    packet: SlicedPacket<'a>,
}

impl <'a> GenericIpPacket<'a> {
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

    fn get_destination_address(&self) -> Some(&[u8]) {
        let ipv4_header = self.get_ipv4_header();
        if ipv4_header.is_some() {
            return Some(&ipv4_header.unwrap().destination);
        }

        let ipv6_header = self.get_ipv6_header();
        if ipv6_header.is_some() {
            return Some(&ipv6_header.unwrap().destination);
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

fn build_response_packet(
    request_packet: Vec<u8>,
    response_payload: Vec<u8>,
) -> Option<Vec<u8>> {
    let generic_request_packet = match GenericIpPacket::from_array(request_packet.as_slice()) {
        Some(value) => value,
        None => return None,
    };

    let request_payload = match generic_request_packet.get_udp_packet() {
        Some(value) => value,
        None => return None,
    };

    match generic_request_packet.get_ipv4_header() {
        Some(header) => {
            return Some(build_packet_v4(
                &header.destination,
                request_payload.destination_port(),
                &header.source,
                request_payload.source_port(),
                header.time_to_live,
                header.identification,
                &response_payload,
            ));
        },
        None => {},
    };

    match generic_request_packet.get_ipv6_header() {
        Some(header) => {
            return Some(build_packet_v6(
                &header.destination,
                request_payload.destination_port(),
                &header.source,
                request_payload.source_port(),
                header.traffic_class,
                header.flow_label,
                header.hop_limit,
                &response_payload,
            ));
        },
        None => {},
    }

    return None;
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

#[uniffi::export(callback_interface)]
pub trait AndroidVpnService: Send + Sync {
    fn protect_socket(&self, socket_fd: i32);
}

struct AdVpn {
    vpn_fd: i32,
    block_fd: i32,
    device_writes: Vec<Vec<u8>>,
    vpn_watchdog: VpnWatchdog,
    wosp_list: WospList,
}

impl AdVpn {
    pub fn new(vpn_fd: i32, block_fd: i32, watchdog_enabled: bool) -> Self {
        AdVpn {
            vpn_fd,
            block_fd,
            device_writes: Vec::new(),
            vpn_watchdog: VpnWatchdog::new(watchdog_enabled),
            wosp_list: WospList::new(),
        }
    }

    pub fn vpn_loop(&mut self, android_vpn_service: Box<dyn AndroidVpnService>, block_logger: Box<dyn BlockLogger>, vpn_fd: i32, block_fd: i32) {
        let mut packet: Vec<u8> = Vec::with_capacity(32767);
        let mut dns_packet_proxy = DnsPacketProxy::new(android_vpn_service, block_logger);
        while self.do_one(vpn_fd, block_fd, &mut dns_packet_proxy, packet.as_mut_slice()) {}
        native_close(vpn_fd);
    }

    fn do_one(&mut self, vpn_fd: i32, block_fd: i32, dns_packet_proxy: &mut DnsPacketProxy, packet: &mut [u8]) -> bool {
        let mut device_poll_fd = pollfd {
            fd: vpn_fd,
            events: libc::POLLIN,
            revents: 0,
        };
        let block_poll_fd = pollfd {
            fd: block_fd,
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
            let poll_fd = pollfd {
                fd: self.wosp_list.list.get(i).unwrap().socket.as_raw_fd(),
                events: libc::POLLIN,
                revents: 0,
            };
            polls.push(poll_fd);
        }

        debug!("doOne: Polling {} file descriptors", polls.len());
        let result = unsafe {
            syscall!(
                linux_syscall::SYS_ppoll,
                polls.as_ptr(),
                self.vpn_watchdog.get_poll_timeout()
            )
        };
        if result.as_usize_unchecked() == 0 {
            self.vpn_watchdog.handle_timeout();
            return true;
        }

        if block_poll_fd.revents != 0 {
            info!("Told to stop VPN");
            return false;
        }

        // Need to do this before reading from the device, otherwise a new insertion there could
        // invalidate one of the sockets we want to read from either due to size or time out
        // constraints
        if !self.wosp_list.list.is_empty() {
            let mut i: i32 = -1;
            self.wosp_list.list.retain_mut(|element| {
                i += 1;
                if polls.get((i as usize) + 2).unwrap().revents & libc::POLLIN != 0 {
                    debug!("Read from DNS socket: {:?}", element.socket);
                    handle_raw_dns_response(&element.packet, &element.socket);
                    false
                } else {
                    true
                }
            });
        }

        if device_poll_fd.revents & libc::POLLOUT != 0 {
            debug!("Write to device");
            // write_to_device(vpn_fd, write);
        }

        if device_poll_fd.revents & libc::POLLIN != 0 {
            debug!("Read from device");
            // read_packet_from_device(vpn_fd, packet)
        }

        return true;
    }

    fn write_to_device(&self, write: Vec<u8>) -> bool {
        let result = unsafe {
            syscall!(
                linux_syscall::SYS_write,
                self.vpn_fd,
                write.as_ptr(),
                write.len()
            )
        }
        .try_isize();
        return match result {
            Ok(value) => value == write.len() as isize,
            Err(_) => false,
        };
    }

    fn read_packet_from_device(&self, vpn_fd: i32, packet: &mut [u8]) {
        todo!()
    }

    fn forward_packet() {
        todo!()
    }

    fn handle_raw_dns_response(&self, parsed_packet: &Vec<u8>, dns_socket: &UdpSocket) {
        // TODO: Find where to put DNS_RESPONSE_PACKET_SIZE = 1024
        let mut datagram_data: Vec<u8> = Vec::with_capacity(1024);
        dns_socket.recv_from(datagram_data.as_mut_slice());
    }

    fn queue_device_write(&self, packet: Vec<u8>) {
        todo!()
    }
}

struct VpnWatchdog {
    enabled: bool,
    init_penalty: u64,
    last_packet_sent: u128,
    last_packet_received: u128,
    poll_timeout: i32,
    target_address: String,
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

    const DNS_PORT: u8 = 53;

    fn new(enabled: bool) -> Self {
        Self {
            enabled,
            init_penalty: 0,
            last_packet_sent: 0,
            last_packet_received: 0,
            poll_timeout: VpnWatchdog::POLL_TIMEOUT_START,
            target_address: String::new(),
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

    fn set_target(&mut self, target: String) {
        self.target_address = target;
    }

    fn handle_packet(&mut self, packet_data: &[u8]) {
        if !self.enabled {
            return;
        }

        debug!(
            "handle_packet: Received packet of length {}",
            packet_data.len()
        );
        self.last_packet_received = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .unwrap()
            .as_millis();
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
                socket.send_to(vec![].as_slice(), &self.target_address);
                self.last_packet_sent = SystemTime::now()
                    .duration_since(UNIX_EPOCH)
                    .unwrap()
                    .as_millis();
            }
            Err(error) => error!("Failed to send watchdog packet! - {}", error.kind()),
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
            time: SystemTime::now()
                .duration_since(UNIX_EPOCH)
                .unwrap()
                .as_millis(),
        }
    }

    fn age_seconds(&self) -> u128 {
        return (SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .unwrap()
            .as_millis()
            - self.time)
            / 1000;
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
                "Dropping socket due to space constraints: {:?}",
                self.list.first().unwrap().packet
            );
            self.list.remove(0);
        }

        while !self.list.is_empty()
            && self.list.first().unwrap().age_seconds() > Self::DNS_TIMEOUT_SEC
        {
            debug!("Timeout on socket {:?}", self.list.first().unwrap().socket);
            self.list.remove(0);
        }

        self.list.push(wosp);
    }
}

#[derive(uniffi::Enum, PartialEq, PartialOrd)]
enum HostState {
    IGNORE,
    DENY,
    ALLOW,
}

#[derive(uniffi::Object)]
struct Host {
    title: String,
    data: String,
    state: HostState,
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

    fn initialize(&mut self, host_items: Vec<Host>, host_exceptions: Vec<Host>) {
        info!("Loading block list");

        let mut new_set: HashSet<u64> = HashSet::new();

        let mut sorted_host_items = host_items
            .iter()
            .filter(|item| item.state != HostState::IGNORE)
            .collect::<Vec<&Host>>();
        sorted_host_items.sort_by(|a, b| a.state.partial_cmp(&b.state).unwrap());

        for item in sorted_host_items.iter() {
            self.load_item(&mut new_set, item);
        }

        let mut sorted_host_exceptions = host_exceptions
            .iter()
            .filter(|item| item.state != HostState::IGNORE)
            .collect::<Vec<&Host>>();
        sorted_host_exceptions.sort_by(|a, b| a.state.partial_cmp(&b.state).unwrap());

        for exception in sorted_host_exceptions {
            self.add_host(&mut new_set, &exception.state, &exception.data);
        }

        self.blocked_hosts = Arc::new(new_set);
    }

    fn load_item(&mut self, set: &mut HashSet<u64>, host: &Host) {
        if host.state == HostState::IGNORE {
            return;
        }

        let file = File::open(&host.data);
        if file.is_err() {
            self.add_host(set, &host.state, &host.data);
        }

        let lines: io::Lines<io::BufReader<File>> = io::BufReader::new(file.unwrap()).lines();
        self.load_file(set, &host, lines);
    }

    fn add_host<T: Hash>(&mut self, set: &mut HashSet<u64>, state: &HostState, data: &T) {
        let mut hasher = DefaultHasher::new();
        data.hash(&mut hasher);
        let hash = hasher.finish();
        match state {
            HostState::IGNORE => return,
            HostState::DENY => set.insert(hash),
            HostState::ALLOW => set.remove(&hash),
        };
    }

    fn load_file(
        &mut self,
        set: &mut HashSet<u64>,
        host: &Host,
        lines: io::Lines<io::BufReader<File>>,
    ) {
        let mut count = 0;
        for line in lines {
            if line.is_err() {
                error!(
                    "load_file: Error while reading {} after {} lines",
                    &host.data, count
                );
                return;
            }

            let data = Self::parse_line(line.unwrap().as_str());
            if data.is_some() {
                self.add_host(set, &host.state, &data.unwrap());
            }

            count += 1;
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
pub trait BlockLogger: Send + Sync {
    fn log(&self, connection_name: String, allowed: bool);
}

struct DnsPacketProxy {
    android_vpn_service: Box<dyn AndroidVpnService>,
    block_logger: Box<dyn BlockLogger>,
    rule_database: RuleDatabase,
    upstream_dns_servers: Vec<Vec<u8>>,
}

impl DnsPacketProxy {
    fn new(
        android_vpn_service: Box<dyn AndroidVpnService>,
        block_logger: Box<dyn BlockLogger>,
    ) -> Self {
        DnsPacketProxy {
            android_vpn_service,
            block_logger,
            rule_database: RuleDatabase::new(),
            upstream_dns_servers: Vec::new(),
        }
    }

    fn initialize(
        &mut self,
        host_items: Vec<Host>,
        host_exceptions: Vec<Host>,
        upstream_dns_servers: Vec<Vec<u8>>,
    ) {
        self.rule_database.initialize(host_items, host_exceptions);
        self.upstream_dns_servers = upstream_dns_servers;
    }

    fn handle_dns_response(&self, ad_vpn: &AdVpn, request_packet: Vec<u8>, response_payload: Vec<u8>) {
        match build_response_packet(request_packet, response_payload) {
            Some(packet) => ad_vpn.queue_device_write(packet),
            None => return,
        };
    }

    fn handle_dns_request(&self, ad_vpn: &AdVpn, packet_data: &[u8]) {
        let packet = match GenericIpPacket::from_array(packet_data) {
            Some(value) => value,
            None => {
                warn!("handle_dns_request: Failed to parse packet data - {:?}", packet_data);
                return;
            },
        };

        // TODO: We currently assume that DNS requests will be UDP only. This is not true.
        let udp_packet = match packet.get_udp_packet() {
            Some(value) => value,
            None => {
                warn!("handle_dns_request: Failed to get UDP packet from generic packet - {:?}", packet);
                return;
            },
        };

        let destination_address = match packet.get_destination_address() {
            Some(value) => value,
            None => {
                warn!("handle_dns_request: Failed to get destination address for packet - {:?}", packet);
                return;
            },
        };
        let real_destination_address = match self.translate_destination_address(destination_address) {
            Some(value) => value,
            None => {
                warn!("handle_dns_request: Failed to translate destination address - {:?}", destination_address);
                return;
            },
        };

        let destination_port = udp_packet.destination_port();
        let dns_packet = match dns_parser::Packet::parse(udp_packet.payload()) {
            Ok(value) => value,
            Err(e) => {
                warn!("handle_dns_request: Discarding no-DNS or invalid packet - {}", e.to_string());
                return;
            },
        };

        if dns_packet.questions.is_empty() {
            warn!("handle_dns_request: Discarding DNS packet with no query - {:?}", dns_packet);
            return;
        }

        // TODO: Do this without allocating an extra string
        let dns_query_name = dns_packet.questions.first().unwrap().qname.to_string().to_lowercase();
        if self.rule_database.is_blocked(&dns_query_name) {
            info!("handle_dns_request: DNS Name {} allowed. Sending to {:?}", dns_query_name, real_destination_address);
            self.block_logger.log(dns_query_name, true);

            // TODO: Need to figure out "Datagram Packets"
        } else {
            info!("handle_dns_request: DNS Name {} blocked!", dns_query_name);
            self.block_logger.log(dns_query_name, false);

            // TODO: Idk what to do here yet
            dns_parser::Builder::new_query(dns_packet.header.id, dns_packet.header.recursion_desired).build();
        }
    }

    fn translate_destination_address(&self, destination_address: Vec<u8>) -> Option<Vec<u8>> {
        todo!()
    }
}
