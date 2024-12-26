use std::{
    io::Error,
    net::UdpSocket,
    os::fd::AsRawFd,
    thread,
    time::{self, SystemTime, UNIX_EPOCH},
};

use android_logger::Config;
use etherparse::{
    err::packet, ip_number, IpHeaders, IpNumber, Ipv4Header, Ipv4HeaderSlice, Ipv6FlowLabel,
    Ipv6Header, Ipv6HeaderSlice, Ipv6Slice, NetSlice, PacketBuilder, PacketBuilderStep,
    SlicedPacket, TransportSlice, UdpSlice,
};
use libc::{pollfd, sock_extended_err};
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

#[derive(uniffi::Object)]
pub struct GenericIpPacket {
    data: Vec<u8>,
}

#[uniffi::export]
impl GenericIpPacket {
    #[uniffi::constructor]
    pub fn new(data: Vec<u8>) -> Self {
        Self { data }
    }

    pub fn get_ipv4_header(&self) -> Option<Vec<u8>> {
        let packet = match SlicedPacket::from_ip(&self.data) {
            Ok(value) => value,
            Err(value) => {
                error!("Err {:?}", value);
                return None;
            }
        };

        return match packet.net {
            Some(net) => match net {
                NetSlice::Ipv4(value) => Some(value.header().slice().to_vec()),
                NetSlice::Ipv6(_) => None,
            },
            None => None,
        };
    }

    pub fn get_ipv6_header(&self) -> Option<Vec<u8>> {
        let packet = match SlicedPacket::from_ip(&self.data) {
            Ok(value) => value,
            Err(value) => {
                error!("Err {:?}", value);
                return None;
            }
        };

        return match packet.net {
            Some(net) => match net {
                NetSlice::Ipv4(_) => None,
                NetSlice::Ipv6(value) => Some(value.header().slice().to_vec()),
            },
            None => None,
        };
    }

    pub fn get_destination_address(&self) -> Option<Vec<u8>> {
        let ipv4_header = self.get_ipv4_header();
        if ipv4_header.is_some() {
            return match Ipv4HeaderSlice::from_slice(ipv4_header.unwrap().as_slice()) {
                Ok(header) => Some(header.to_header().destination.to_vec()),
                Err(_) => None,
            };
        }

        let ipv6_header = self.get_ipv6_header();
        if ipv6_header.is_some() {
            return match Ipv6HeaderSlice::from_slice(ipv6_header.unwrap().as_slice()) {
                Ok(header) => Some(header.to_header().destination.to_vec()),
                Err(_) => None,
            };
        }

        return None;
    }

    pub fn get_udp_packet(&self) -> Option<Vec<u8>> {
        let packet = match SlicedPacket::from_ip(&self.data) {
            Ok(value) => value,
            Err(value) => {
                error!("Err {:?}", value);
                return None;
            }
        };

        return match packet.transport {
            Some(transport) => match transport {
                TransportSlice::Udp(udp) => Some(udp.slice().to_vec()),
                _ => None,
            },
            None => None,
        };
    }
}

#[derive(uniffi::Object)]
pub struct UdpPacket {
    data: Vec<u8>,
}

#[uniffi::export]
impl UdpPacket {
    #[uniffi::constructor]
    pub fn new(data: Vec<u8>) -> Self {
        Self { data }
    }

    pub fn has_payload(&self) -> bool {
        let packet = TransportSlice::Udp(UdpSlice::from_slice(&self.data.as_slice()).unwrap());
        return match packet {
            TransportSlice::Udp(udp) => udp.payload().len() > 0,
            _ => false,
        };
    }

    pub fn get_payload(&self) -> Option<Vec<u8>> {
        let packet = TransportSlice::Udp(UdpSlice::from_slice(&self.data.as_slice()).unwrap());
        return match packet {
            TransportSlice::Udp(udp) => Some(udp.payload().to_vec()),
            _ => None,
        };
    }

    pub fn get_destination_port(&self) -> u16 {
        let packet = TransportSlice::Udp(UdpSlice::from_slice(&self.data.as_slice()).unwrap());
        return match packet {
            TransportSlice::Udp(udp) => udp.to_header().destination_port,
            _ => 0,
        };
    }
}

#[uniffi::export]
pub fn build_response_packet(
    request_packet: Vec<u8>,
    response_payload: Vec<u8>,
) -> Option<Vec<u8>> {
    let generic_request_packet = GenericIpPacket::new(request_packet);
    let binding = generic_request_packet.get_udp_packet().unwrap();
    let request_payload =
        match TransportSlice::Udp(UdpSlice::from_slice(binding.as_slice()).unwrap()) {
            TransportSlice::Udp(udp) => udp,
            _ => return None,
        };
    let ipv4_header = generic_request_packet.get_ipv4_header();
    if ipv4_header.is_some() {
        let real_ipv4_header = ipv4_header.unwrap();
        let header = match Ipv4HeaderSlice::from_slice(&real_ipv4_header.as_slice()) {
            Ok(value) => value,
            Err(_) => return None,
        };
        return Some(build_packet_v4(
            &header.destination(),
            request_payload.destination_port(),
            &header.source(),
            request_payload.source_port(),
            header.ttl(),
            header.identification(),
            &response_payload,
        ));
    }

    let ipv6_header = generic_request_packet.get_ipv6_header();
    if ipv6_header.is_some() {
        let real_ipv6_header = ipv6_header.unwrap();
        let header = match Ipv6HeaderSlice::from_slice(&real_ipv6_header.as_slice()) {
            Ok(value) => value,
            Err(_) => return None,
        };
        return Some(build_packet_v6(
            &header.destination(),
            request_payload.destination_port(),
            &header.source(),
            request_payload.source_port(),
            header.traffic_class(),
            header.flow_label(),
            header.hop_limit(),
            &response_payload,
        ));
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
pub fn native_close(fd: i32) -> bool {
    match unsafe { syscall!(linux_syscall::SYS_close, fd) }.check() {
        Ok(_) => true,
        Err(_) => false,
    }
}

#[uniffi::export]
pub fn vpn_loop(vpn_fd: i32, block_fd: i32, watchdog_enabled: bool) {
    let mut device_writes: Vec<Vec<u8>> = Vec::new();
    let vpn_watchdog = VpnWatchdog::new(watchdog_enabled);
    let wosp_list = WospList::new();
    while do_one(
        vpn_fd,
        block_fd,
        &mut device_writes,
        &vpn_watchdog,
        &wosp_list,
    ) {}
}

fn do_one(
    vpn_fd: i32,
    block_fd: i32,
    device_writes: &mut Vec<Vec<u8>>,
    vpn_watchdog: &VpnWatchdog,
    dns_in: &WospList,
) -> bool {
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

    if !device_writes.is_empty() {
        device_poll_fd.events = device_poll_fd.events | libc::POLLOUT;
    }

    let length = dns_in.list.len();
    let mut polls: Vec<pollfd> = Vec::with_capacity(2 + length);
    polls.push(device_poll_fd);
    polls.push(block_poll_fd);
    for i in 0..length {
        let poll_fd = pollfd {
            fd: dns_in.list.get(i).unwrap().socket.as_raw_fd(),
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
            vpn_watchdog.get_poll_timeout()
        )
    };
    if result.as_usize_unchecked() == 0 {
        vpn_watchdog.handle_timeout();
        return true;
    }

    if block_poll_fd.revents != 0 {
        info!("Told to stop VPN");
        return false;
    }

    // Need to do this before reading from the device, otherwise a new insertion there could
    // invalidate one of the sockets we want to read from either due to size or time out
    // constraints
    if !dns_in.list.is_empty() {
        let mut i: i32 = -1;
        dns_in.list.retain_mut(|element| {
            i += 1;
            if polls.get((i as usize) + 2).unwrap().revents & libc::POLLIN != 0 {
                debug!("Read from DNS socket: {:?}", element.socket);
                handle_raw_dns_response(element.packet, element.socket);
                false
            } else {
                true
            }
        });
    }

    if device_poll_fd.revents & libc::POLLOUT != 0 {
        debug!("Write to device");
        write_to_device(vpn_fd, write);
    }

    if device_poll_fd.revents & libc::POLLIN != 0 {
        debug!("Read from device");
        // TODO: Read from device
    }

    return true;
}

fn write_to_device(vpn_fd: i32, write: Vec<u8>) -> bool {
    let result = unsafe {
        syscall!(
            linux_syscall::SYS_write,
            vpn_fd,
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

fn handle_raw_dns_response(parsed_packet: Vec<u8>, dns_socket: UdpSocket) {
    // TODO: Find where to put DNS_RESPONSE_PACKET_SIZE = 1024
    let datagram_data: Vec<u8> = Vec::with_capacity(1024);
    let mut reply_packet = UdpPacket::new(datagram_data);
    dns_socket.recv_from(&mut reply_packet.data);
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
