/*
 * Copyright 2020-present Open Networking Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package nctu.pncourse.p4bridge;

import com.google.common.collect.ImmutableSet;
import org.onosproject.cfg.ComponentConfigService;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.onosproject.net.pi.model.PiActionId;
import org.onosproject.net.pi.model.PiActionParamId;
import org.onosproject.net.pi.model.PiMatchFieldId;
import org.onosproject.net.pi.model.PiTableId;
import org.onosproject.net.pi.runtime.PiAction;
import org.onosproject.net.pi.runtime.PiActionParam;
import org.onosproject.net.flow.criteria.PiCriterion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Dictionary;
import java.util.Properties;

import static org.onlab.util.Tools.get;

// Imported be coder
import com.google.common.collect.Maps;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;

import org.onlab.packet.Ethernet;
import org.onosproject.net.packet.PacketPriority;
import org.onosproject.net.packet.PacketService;
import org.onosproject.net.packet.PacketProcessor;
import org.onosproject.net.packet.PacketContext;
import org.onosproject.net.packet.InboundPacket;
import org.onosproject.net.DeviceId;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.PortNumber;
import org.onosproject.net.ConnectPoint;


import org.onosproject.net.flow.DefaultFlowRule;
import org.onosproject.net.flow.FlowRule;
import org.onosproject.net.flow.FlowRuleService;

import org.onosproject.net.flow.TrafficTreatment;
import org.onosproject.net.flow.DefaultTrafficTreatment;

import java.util.*;
// import javafx.util.Pair;

import org.onlab.packet.MacAddress;
/**
 * Skeletal ONOS application component.
 */
@Component(immediate = true/*,
           service = {SomeInterface.class},
            property = {
               "someProperty=Some Default String Value",
           } */)
public class AppComponent {

    private final Logger log = LoggerFactory.getLogger(getClass());
    private ReactivePacketProcessor processor = new ReactivePacketProcessor();
    private ApplicationId appId;

    protected Map<DeviceId, Map<MacAddress, PortNumber>> macTables = Maps.newConcurrentMap();

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected PacketService packetService;

    // @Reference(cardinality = ReferenceCardinality.MANDATORY)
    // protected HostService hostService;

    // @Reference(cardinality = ReferenceCardinality.MANDATORY)
    // protected TopologyService topologyService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected FlowRuleService flowRuleService;

    /** Some configurable property. */    
    // private String someProperty;

    // @Reference(cardinality = ReferenceCardinality.MANDATORY)
    // protected ComponentConfigService cfgService;

    @Activate
    protected void activate() {
        // cfgService.registerProperties(getClass());
        appId = coreService.registerApplication("nctu.pncourse.p4-learning-bridge");
        packetService.addProcessor(processor, PacketProcessor.director(2));  
        packetService.requestPackets(DefaultTrafficSelector.builder()
            .matchEthType(Ethernet.TYPE_IPV4).build(), PacketPriority.REACTIVE, appId, Optional.empty());
        packetService.requestPackets(DefaultTrafficSelector.builder()
                .matchEthType(Ethernet.TYPE_ARP).build(), PacketPriority.REACTIVE, appId, Optional.empty());
        log.info("GoGo");
    }

    @Deactivate
    protected void deactivate() {
        // cfgService.unregisterProperties(getClass(), false);
        packetService.removeProcessor(processor);
        processor = null;
        log.info("Stopped");
    }

    private class ReactivePacketProcessor implements PacketProcessor {
        @Override
        public void process(PacketContext context) {
            // if (context.isHandled()) 
            //     return;

            // To simplify parameter's length
            Ethernet ethPkt = context.inPacket().parsed();
            // if (ethPkt == null)
            //     return;

            if (ethPkt.getEtherType() != Ethernet.TYPE_IPV4 && ethPkt.getEtherType() != Ethernet.TYPE_ARP) {
                return;
            }

            ConnectPoint cp = context.inPacket().receivedFrom();
            MacAddress srcMac = ethPkt.getSourceMAC();
            MacAddress dstMac = ethPkt.getDestinationMAC();

            // Create new device if it is not in mactables
            macTables.putIfAbsent(cp.deviceId(), Maps.newConcurrentMap());
            Map<MacAddress, PortNumber> macTable = macTables.get(cp.deviceId());

            // Put input mac and its port into the current device
            macTable.putIfAbsent(srcMac, cp.port());
            log.info("Daniel Packet In: Device, Mac, port: " + cp.deviceId() + " " + srcMac + " "+ cp.port());
            log.info("Daniel Wanna Find: " + dstMac);

            PortNumber outPort = macTable.get(dstMac);

            if (outPort == null) {
                log.info("Daniel: Broadcast " + dstMac);
                packetOut(context, cp.port(), PortNumber.FLOOD);
                return;
            }
            else {
                installRule(context, srcMac, dstMac, outPort);
                packetOut(context, cp.port(), outPort);
                return;
            }
        }
    

        // // It should be correct
        // public void flood(PacketContext context) {
        //     // if (topologyService.isBroadcastPoint(topologyService.currentTopology(), context.inPacket().receivedFrom()))
        //         packetOut(context, PortNumber.FLOOD);
        //     // else
        //     //     context.block();
        // }

        // It should be correct
        public void packetOut(PacketContext context, PortNumber inPort, PortNumber outPortNumber) {
            context.treatmentBuilder().setOutput(outPortNumber).writeMetadata(inPort.toLong(), 0b1111111110000000);
            // context.treatmentBuilder().setOutput(outPortNumber).writeMetadata(inPort.toLong(), 0b1111111111111111);
            context.send();
        }

        public void installRule(PacketContext context, MacAddress srcMac, MacAddress dstMac, PortNumber outPort) {

            log.info("Daniel install: " + context.inPacket().receivedFrom().deviceId() + " " + srcMac  + " \n" +  dstMac  + " " +  outPort);

            // Set table match field
            PiTableId forwardTableId = PiTableId.of("c_ingress.t_l2_fwd");
            PiMatchFieldId dstMacMatchFieldId = PiMatchFieldId.of("hdr.ethernet.dst_addr");
            PiCriterion match = PiCriterion.builder()
                .matchExact(dstMacMatchFieldId, dstMac.toBytes())
                .build();

            // Set action param
            PiActionParamId portParamId = PiActionParamId.of("port");
            PiActionParam portParam = new PiActionParam(portParamId, (short) outPort.toLong());

            // Set output port action
            final PiAction action;
            PiActionId egressActionId = PiActionId.of("c_ingress.set_out_port");
            action = PiAction.builder()
                .withId(egressActionId)
                .withParameter(portParam)
                .build();

            // Add flow rule to device
            FlowRule fr = DefaultFlowRule.builder()
                .forTable(forwardTableId)
                .withSelector(DefaultTrafficSelector.builder().matchPi(match).build())
                .withTreatment(DefaultTrafficTreatment.builder().piTableAction(action).build())
                .forDevice(context.inPacket().receivedFrom().deviceId())
                .withPriority(PacketPriority.REACTIVE.priorityValue())
                .makePermanent()
                .fromApp(appId).build();
            flowRuleService.applyFlowRules(fr);
        }
    }
}