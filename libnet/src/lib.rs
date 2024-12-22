use android_logger::Config;
use etherparse::{
    err::packet, ip_number, IpHeaders, IpNumber, Ipv4Header, Ipv4HeaderSlice, Ipv6FlowLabel, Ipv6Header, Ipv6HeaderSlice, Ipv6Slice, NetSlice, PacketBuilder, PacketBuilderStep, SlicedPacket, TransportSlice, UdpSlice
};
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
    pub data: Vec<u8>,
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
    pub data: Vec<u8>,
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
pub fn build_response_packet(request_packet: Vec<u8>, response_payload: Vec<u8>) -> Option<Vec<u8>> {
    let generic_request_packet = GenericIpPacket::new(request_packet);
    let binding = generic_request_packet.get_udp_packet().unwrap();
    let request_payload = match TransportSlice::Udp(
        UdpSlice::from_slice(binding.as_slice()).unwrap(),
    ) {
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
