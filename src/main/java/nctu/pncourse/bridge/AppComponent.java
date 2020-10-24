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
package nctu.pncourse.bridge;

import com.google.common.collect.ImmutableSet;
import org.onosproject.cfg.ComponentConfigService;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Dictionary;
import java.util.Properties;

import static org.onlab.util.Tools.get;

// Imported be coder
import org.onlab.packet.Ethernet;
import org.onosproject.net.flowobjective.DefaultForwardingObjective;
import org.onosproject.net.flowobjective.ForwardingObjective;
import org.onosproject.net.packet.PacketPriority;
import org.onosproject.net.packet.PacketService;
import org.onosproject.net.packet.PacketProcessor;
import org.onosproject.net.packet.PacketContext;
import org.onosproject.net.packet.InboundPacket;
import org.onosproject.net.Host;
import org.onosproject.net.HostId;
import org.onosproject.net.host.HostService;
import org.onosproject.net.topology.TopologyService;
import org.onosproject.net.DeviceId;

import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.PortNumber;

import org.onosproject.net.flow.TrafficTreatment;
import org.onosproject.net.flow.DefaultTrafficTreatment;

import org.onosproject.net.flowobjective.FlowObjectiveService;
import java.util.*;
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
    protected Map<DeviceId, Map<MacAddress, Portnumber>> macTables = Maps.newConcurrentMap();

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected PacketService packetService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected HostService hostService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected TopologyService topologyService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected FlowObjectiveService flowObjectiveService;

    /** Some configurable property. */    
    // private String someProperty;

    // @Reference(cardinality = ReferenceCardinality.MANDATORY)
    // protected ComponentConfigService cfgService;

    @Activate
    protected void activate() {
        // cfgService.registerProperties(getClass());
        appId = coreService.registerApplication("nctu.pncourse.demo");
        packetService.addProcessor(processor, PacketProcessor.directory(2));  
        TrafficSelector.builder selector = DefaultTrafficSelector.builder();
        selector.matchEthType(Ethernet.TYPE_IPV4);  // Does arp have to be match>

        packetService.requestPackets(selector.build(), PacketPriority.REACTIVE, appId);
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
            if (context.isHandled()) 
                return;
            InboundPacket pkt = context.inpacket();
            Ethernet ethPkt = pkt.parse();
            if (ethPkt == null)
                return;

            HostId srcMAC = HostId.hostId(ethPkt.getSourceMac());
            HostId dstMAC = HostId.hostId(ethPkt.getDestinationMAC());
            Host dst = hostService.getHost(dstMAC);

            // Check whether inpacke is in the MAC table
            if (macTables.get(pc.inpacket().receivedFrom()).deviceId() != null) {
                macTables.remove(pc.inpacket().receivedFrom().deviceId());
                installRule(context, srcMAC, dstMAC);
                packetOut(context, PortNumber.TABLE);
                return;
            }
            else {
                // push into map
                macTables.putIfAbsent(pc.inpacket().receivedFrom().deviceId(), Map.newConcurrentMap());
            }

            if (dst == null)     // Do we have to match arp?
            {
                flood(context);
                return;
            }
            installRule(context, srcMAC, dstMAC);
            packetOut(context, PortNumber.TABLE);
            return;
        }
    }

    private void flood(PacketContext context) {
        if (topologyService.isBroadcastPoint(topologyService.currentTopology(), context.inpacket().receivedFrom()))
            packetOut(context, Portnumber.FLOOD);
        else
            context.block();
    }

    private void packetOut(PacketContext context, PortNumber portnumber) {
        context.treatmentBuilder().setOutput(portnumber);
        context.send();
    }

    private void installRule(PacketContext context, HostId srcMAC, HostId dstMAC) {
        Ethernet inPkt = context.inPacket().parsed();
        TrafficSelector.Builder selectorBuilder = DefaultTrafficSelector.builder();
        Host dst = hostService.getHost(dstMAC);
        Host src = hostService.getHost(srcMAC);

        if(src == null || dst == null){
            return;
        } else
        {
            selectorBuilder.matchEthSrc(inPkt.getSourceMAC())
                    .matchEthDst(inPkt.getDestinationMAC());

            TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                    .setOutput(dst.location().port())
                    .build();

            ForwardingObjective forwardingObjective = DefaultForwardingObjective.builder()
                    .withSelector(selectorBuilder.build())
                    .withTreatment(treatment)
                    .withPriority(10)
                    .withFlag(ForwardingObjective.Flag.VERSATILE)
                    .fromApp(appId)
                    .makeTemporary(10)
                    .add();

            flowObjectiveService.forward(context.inPacket().receivedFrom().deviceId(), forwardingObjective);
        }
    }

    // @Modified
    // public void modified(ComponentContext context) {
    //     Dictionary<?, ?> properties = context != null ? context.getProperties() : new Properties();
    //     if (context != null) {
    //         someProperty = get(properties, "someProperty");
    //     }
    //     log.info("Reconfigured");
    // }

    // @Override
    // public void someMethod() {
    //     log.info("Invoked");
    // }
}
