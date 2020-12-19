/* -*- P4_16 -*- */
#include <core.p4>
#include <v1model.p4>

const bit<16> ETH_TYPE_IPV4 = 0x0800;
const bit<16> ETH_TYPE_ARP = 0x0806;

typedef bit<9> port_t;
const port_t CPU_PORT = 255;
/*************************************************************************
*********************** H E A D E R S  ***********************************
*************************************************************************/

header ethernet_t {
    bit<48> dst_addr;
    bit<48> src_addr;
    bit<16> ether_type;
}

header ipv4_t {
    bit<4>  version;
    bit<4>  ihl;
    bit<8>  diffserv;
    bit<16> len;
    bit<16> identification;
    bit<3>  flags;
    bit<13> frag_offset;
    bit<8>  ttl;
    bit<8>  protocol;
    bit<16> hdr_checksum;
    bit<32> src_addr;
    bit<32> dst_addr;
}

@controller_header("packet_in")
header packet_in_header_t {
    bit<9> ingress_port;
    bit<7> _padding;
}

@controller_header("packet_out")
header packet_out_header_t {
    bit<9> egress_port;
    bit<7> _padding;
}

struct metadata_t {
    /* empty */
}

struct headers_t {
    ethernet_t ethernet;
    ipv4_t ipv4;
    packet_out_header_t packet_out;
    packet_in_header_t packet_in;
}


/*************************************************************************
*********************** P A R S E R  ***********************************
*************************************************************************/

parser c_parser(packet_in packet,
                out headers_t hdr,
                inout metadata_t meta,
                inout standard_metadata_t standard_metadata) {

    state start {
        transition select(standard_metadata.ingress_port) {
            CPU_PORT: parse_packet_out;
            default: parse_ethernet;
        }
    }

    state parse_packet_out {
        packet.extract(hdr.packet_out);
        transition parse_ethernet;
    }

    state parse_ethernet {
        packet.extract(hdr.ethernet);
        transition select(hdr.ethernet.ether_type) {
            ETH_TYPE_IPV4: parse_ipv4;
            default: accept;
        }
    }

     state parse_ipv4 {
        packet.extract(hdr.ipv4);
        transition accept;
    }
}


/*************************************************************************
************   C H E C K S U M    V E R I F I C A T I O N   *************
*************************************************************************/

control c_verify_checksum(inout headers_t hdr, inout metadata_t meta) {
    apply {  }
}


/*************************************************************************
**************  I N G R E S S   P R O C E S S I N G   *******************
*************************************************************************/

control c_ingress(inout headers_t hdr,
                  inout metadata_t meta,
                  inout standard_metadata_t standard_metadata) {

    action send_to_cpu() {
        standard_metadata.egress_spec = CPU_PORT;
        // Packets sent to the controller needs to be prepended with the
        // packet-in header. By setting it valid we make sure it will be
        // deparsed on the wire (see c_deparser).
        hdr.packet_in.setValid();
        hdr.packet_in.ingress_port = standard_metadata.ingress_port;
    }

    action set_out_port(port_t port) {
        // Specifies the output port for this packet by setting the
        // corresponding metadata.
        standard_metadata.egress_spec = port;
    }

    // action _drop() {
    //     mark_to_drop(standard_metadata);
    // }

    // action set_mcast_grp(bit<16> mcast_grp) {
    //     standard_metadata.mcast_grp = mcast_grp;
    // }

    table t_l2_fwd {
        key = {
            // standard_metadata.ingress_port  : ternary;
            hdr.ethernet.dst_addr           : exact;
            hdr.ethernet.ether_type         : exact;
        }
        actions = {
            set_out_port;
            send_to_cpu;
            // _drop;
            // NoAction;
        }
        default_action = send_to_cpu();
    }

    // action set_mcast_grp(bit<16> mcast_grp) {
    //     standard_metadata.mcast_grp = mcast_grp;
    // }

    // table broadcast {
    //     key = {
    //         standard_metadata.ingress_port: exact;
    //     }

    //     actions = {
    //         set_mcast_grp;
    //         NoAction;
    //     }
    //     size = 256;
    //     default_action = NoAction;
    // }

    apply {
        if (standard_metadata.ingress_port == CPU_PORT) {
            // Packet received from CPU_PORT, this is a packet-out sent by the
            // controller. Skip table processing, set the egress port as
            // requested by the controller (packet_out header) and remove the
            // packet_out header.
            standard_metadata.egress_spec = hdr.packet_out.egress_port;
            hdr.packet_out.setInvalid();

            // broadcast.apply();
        } else {
            // Packet received from data plane port.
            // Applies table t_l2_fwd to the packet.
            if (t_l2_fwd.apply().hit) {
                // Packet hit an entry in t_l2_fwd table. A forwarding action
                // has already been taken. No need to apply other tables, exit
                // this control block.
                return;
            }
        }
    }
}

/*************************************************************************
****************  E G R E S S   P R O C E S S I N G   *******************
*************************************************************************/

control c_egress(inout headers_t hdr,
                 inout metadata_t meta,
                 inout standard_metadata_t standard_metadata) {

    apply {

        // If ingress clone
        // if (standard_metadata.instance_type == 1){
        //     hdr.cpu.setValid();
        //     hdr.cpu.srcAddr = hdr.ethernet.srcAddr;
        //     hdr.cpu.ingress_port = (bit<16>)meta.ingress_port;
        //     hdr.ethernet.etherType = L2_LEARN_ETHER_TYPE;
        //     truncate((bit<32>)22); //ether+cpu header
    }
}

/*************************************************************************
*************   C H E C K S U M    C O M P U T A T I O N   **************
*************************************************************************/

control c_compute_checksum(inout headers_t hdr, inout metadata_t meta) {
     apply {

    }
}

/*************************************************************************
***********************  D E P A R S E R  *******************************
*************************************************************************/

control c_deparser(packet_out packet, in headers_t hdr) {
    apply {
        //parsed headers have to be added again into the packet.
        packet.emit(hdr.packet_in);
        packet.emit(hdr.ethernet);
        packet.emit(hdr.ipv4);
    }
}

/*************************************************************************
***********************  S W I T C H  *******************************
*************************************************************************/

//switch architecture
V1Switch(c_parser(),
         c_verify_checksum(),
         c_ingress(),
         c_egress(),
         c_compute_checksum(),
         c_deparser()) main;