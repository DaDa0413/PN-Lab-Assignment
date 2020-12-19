/*
 * Copyright 2017-present Open Networking Foundation
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

package nctu.pncouse.pipeconf;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import org.onlab.packet.DeserializationException;
import org.onlab.packet.Ethernet;
import org.onlab.util.ImmutableByteSequence;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.DeviceId;
import org.onosproject.net.Port;
import org.onosproject.net.PortNumber;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.driver.AbstractHandlerBehaviour;
import org.onosproject.net.flow.TrafficTreatment;
import org.onosproject.net.flow.criteria.Criterion;
import org.onosproject.net.flow.instructions.Instruction;
import org.onosproject.net.flow.instructions.Instructions.OutputInstruction;
import org.onosproject.net.packet.DefaultInboundPacket;
import org.onosproject.net.packet.InboundPacket;
import org.onosproject.net.packet.OutboundPacket;
import org.onosproject.net.pi.model.PiActionId;
import org.onosproject.net.pi.model.PiActionParamId;
import org.onosproject.net.pi.model.PiMatchFieldId;
import org.onosproject.net.pi.model.PiPacketMetadataId;
import org.onosproject.net.pi.model.PiPipelineInterpreter;
import org.onosproject.net.pi.model.PiTableId;
import org.onosproject.net.pi.runtime.PiAction;
import org.onosproject.net.pi.runtime.PiActionParam;
import org.onosproject.net.pi.runtime.PiPacketMetadata;
import org.onosproject.net.pi.runtime.PiPacketOperation;

import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.List;
import static java.util.stream.Collectors.toList;
import java.util.Map;
import java.util.Optional;

import static java.lang.String.format;
import static org.onlab.util.ImmutableByteSequence.copyFrom;
import static org.onosproject.net.PortNumber.CONTROLLER;
import static org.onosproject.net.PortNumber.FLOOD;
import static org.onosproject.net.flow.instructions.Instruction.Type.OUTPUT;
import static org.onosproject.net.pi.model.PiPacketOperationType.PACKET_OUT;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
/**
 * Implementation of a pipeline interpreter for the mytunnel.p4 program.
 */
public final class PipelineInterpreterImpl
        extends AbstractHandlerBehaviour
        implements PiPipelineInterpreter {
        private final Logger log = LoggerFactory.getLogger(getClass());


        private static final int V1MODEL_PORT_BITWIDTH = 9;

        private static final String DOT = ".";
        private static final String HDR = "hdr";
        private static final String C_INGRESS = "c_ingress";
        private static final String T_L2_FWD = "t_l2_fwd";
        private static final String EGRESS_PORT = "egress_port";
        // private static final String INGRESS_PORT = "ingress_port";
        private static final String ETHERNET = "ethernet";
        private static final String STANDARD_METADATA = "standard_metadata";
        private static final int PORT_FIELD_BITWIDTH = 9;

        // private static final PiMatchFieldId INGRESS_PORT_ID =
        //         PiMatchFieldId.of(STANDARD_METADATA + DOT + "ingress_port");
        private static final PiMatchFieldId ETH_DST_ID =
                PiMatchFieldId.of(HDR + DOT + ETHERNET + DOT + "dst_addr");
        private static final PiMatchFieldId ETH_TYPE_ID =
                PiMatchFieldId.of(HDR + DOT + ETHERNET + DOT + "ether_type");

        private static final PiTableId TABLE_L2_FWD_ID =
                PiTableId.of(C_INGRESS + DOT + T_L2_FWD);

        private static final PiActionId ACT_ID_NOP =
                PiActionId.of("NoAction");
        private static final PiActionId ACT_ID_SEND_TO_CPU =
                PiActionId.of(C_INGRESS + DOT + "send_to_cpu");
        private static final PiActionId ACT_ID_SET_EGRESS_PORT =
                PiActionId.of(C_INGRESS + DOT + "set_out_port");

        private static final PiActionParamId ACT_PARAM_ID_PORT =
                PiActionParamId.of("port");


        private static final Map<Integer, PiTableId> TABLE_MAP =
                new ImmutableMap.Builder<Integer, PiTableId>()
                        .put(0, TABLE_L2_FWD_ID)
                        .build();

        private static final Map<Criterion.Type, PiMatchFieldId> CRITERION_MAP =
                ImmutableMap.<Criterion.Type, PiMatchFieldId>builder()
                        // .put(Criterion.Type.IN_PORT, INGRESS_PORT_ID)
                        .put(Criterion.Type.ETH_DST, ETH_DST_ID)
                        .put(Criterion.Type.ETH_TYPE, ETH_TYPE_ID)
                        .build();

        // Return match field ID
        // This part should not be modified
        @Override
        public Optional<PiMatchFieldId> mapCriterionType(Criterion.Type type) {
                return Optional.ofNullable(CRITERION_MAP.get(type));
        }
        // Return the table ID
        // This part should not be modified
        @Override
        public Optional<PiTableId> mapFlowRuleTableId(int flowRuleTableId) {
                return Optional.ofNullable(TABLE_MAP.get(flowRuleTableId));
        }

        // Return the treadment builder to p4-learning-bridge
        @Override
        public PiAction mapTreatment(TrafficTreatment treatment, PiTableId piTableId)
                throws PiInterpreterException {
                throw new PiInterpreterException("Treatment mapping not supported");
        }

        @Override
        public Collection<PiPacketOperation> mapOutboundPacket(OutboundPacket packet)
                throws PiInterpreterException {
                TrafficTreatment treatment = packet.treatment();
                // log.info("Daniel Interpreter1:" + packet.data());
                // log.info("Daniel Interpreter1:" + treatment.writeMetadata());
                log.info("Daniel Interpreter2:" + treatment.writeMetadata().metadata());
                // Packet-out in main.p4 supports only setting the output port,
                // i.e. we only understand OUTPUT instructions.
                List<OutputInstruction> outInstructions = treatment
                        .allInstructions()
                        .stream()
                        .filter(i -> i.type().equals(OUTPUT))
                        .map(i -> (OutputInstruction) i)
                        .collect(toList());

                if (treatment.allInstructions().size() != outInstructions.size()) {
                        // There are other instructions that are not of type OUTPUT.
                        throw new PiInterpreterException("Treatment not supported: " + treatment);
                }

                ImmutableList.Builder<PiPacketOperation> builder = ImmutableList.builder();
                for (OutputInstruction outInst : outInstructions) {
                        if (outInst.port().isLogical() && !outInst.port().equals(FLOOD)) {
                                throw new PiInterpreterException(format(
                                "Packet-out on logical port '%s' not supported",
                                outInst.port()));
                        } else if (outInst.port().equals(FLOOD)) {
                                // To emulate flooding, we create a packet-out operation for
                                // each switch port.
                                final DeviceService deviceService = handler().get(DeviceService.class);
                                for (Port port : deviceService.getPorts(packet.sendThrough())) {
                                        // packet.data.
                                        if (!(treatment.writeMetadata().metadata() == port.number().toLong()))
                                        {
                                                log.info("Daniell: Not Equal:" + port.number().toLong());
                                                builder.add(buildPacketOut(packet.data(), port.number().toLong()));
                                        }
                                        else
                                                log.info("Daniell: Equal:" + port.number().toLong());
                                }
                        } else {
                                // Create only one packet-out for the given OUTPUT instruction.
                                builder.add(buildPacketOut(packet.data(), outInst.port().toLong()));
                        }
                }
                return builder.build();
        }

        @Override
        public InboundPacket mapInboundPacket(PiPacketOperation packetIn, DeviceId deviceId)
                throws PiInterpreterException {

                // Find the ingress_port metadata.
                final String inportMetadataName = "ingress_port";
                Optional<PiPacketMetadata> inportMetadata = packetIn.metadatas()
                        .stream()
                        .filter(meta -> meta.id().id().equals(inportMetadataName))
                        .findFirst();

                if (!inportMetadata.isPresent()) {
                        throw new PiInterpreterException(format(
                                "Missing metadata '%s' in packet-in received from '%s': %s",
                                inportMetadataName, deviceId, packetIn));
                }

                // Build ONOS InboundPacket instance with the given ingress port.

                // 1. Parse packet-in object into Ethernet packet instance.
                final byte[] payloadBytes = packetIn.data().asArray();
                final ByteBuffer rawData = ByteBuffer.wrap(payloadBytes);
                final Ethernet ethPkt;
                try {
                        ethPkt = Ethernet.deserializer().deserialize(
                                payloadBytes, 0, packetIn.data().size());
                } catch (DeserializationException dex) {
                        throw new PiInterpreterException(dex.getMessage());
                }

                // 2. Get ingress port
                final ImmutableByteSequence portBytes = inportMetadata.get().value();
                final short portNum = portBytes.asReadOnlyBuffer().getShort();
                final ConnectPoint receivedFrom = new ConnectPoint(
                        deviceId, PortNumber.portNumber(portNum));

                return new DefaultInboundPacket(receivedFrom, ethPkt, rawData);
        }

        private PiPacketOperation buildPacketOut(ByteBuffer pktData, long portNumber)
            throws PiInterpreterException {

                // Make sure port number can fit in v1model port metadata bitwidth.
                final ImmutableByteSequence portBytes;
                try {
                portBytes = copyFrom(portNumber).fit(V1MODEL_PORT_BITWIDTH);
                } catch (ImmutableByteSequence.ByteSequenceTrimException e) {
                throw new PiInterpreterException(format(
                        "Port number %d too big, %s", portNumber, e.getMessage()));
                }

                // Create metadata instance for egress port.
                // TODO EXERCISE 1: modify metadata names to match P4 program
                // ---- START SOLUTION ----
                final String outPortMetadataName = "egress_port";
                // ---- END SOLUTION ----
                final PiPacketMetadata outPortMetadata = PiPacketMetadata.builder()
                        .withId(PiPacketMetadataId.of(outPortMetadataName))
                        .withValue(portBytes)
                        .build();

                // Build packet out.
                return PiPacketOperation.builder()
                        .withType(PACKET_OUT)
                        .withData(copyFrom(pktData))
                        .withMetadata(outPortMetadata)
                        .build();
        }
}
