/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.gateway.streaming.actors;

import static org.assertj.core.api.Java6Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.stream.Collectors;

import org.eclipse.ditto.model.base.acks.AcknowledgementLabel;
import org.eclipse.ditto.model.base.acks.AcknowledgementLabelNotDeclaredException;
import org.eclipse.ditto.model.base.acks.AcknowledgementLabelNotUniqueException;
import org.eclipse.ditto.model.base.acks.AcknowledgementRequest;
import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.base.auth.DittoAuthorizationContextType;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.protocoladapter.HeaderTranslator;
import org.eclipse.ditto.services.gateway.security.authentication.jwt.JwtAuthenticationResultProvider;
import org.eclipse.ditto.services.gateway.security.authentication.jwt.JwtValidator;
import org.eclipse.ditto.services.gateway.streaming.Connect;
import org.eclipse.ditto.services.gateway.streaming.IncomingSignal;
import org.eclipse.ditto.services.gateway.streaming.StartStreaming;
import org.eclipse.ditto.services.gateway.streaming.StreamingAck;
import org.eclipse.ditto.services.models.acks.config.AcknowledgementConfig;
import org.eclipse.ditto.services.models.acks.config.DefaultAcknowledgementConfig;
import org.eclipse.ditto.services.models.concierge.pubsub.DittoProtocolSub;
import org.eclipse.ditto.services.models.concierge.streaming.StreamingType;
import org.eclipse.ditto.signals.acks.base.Acknowledgement;
import org.eclipse.ditto.signals.acks.base.AcknowledgementCorrelationIdMissingException;
import org.eclipse.ditto.signals.base.Signal;
import org.eclipse.ditto.signals.events.things.ThingDeleted;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import com.typesafe.config.ConfigFactory;

import akka.actor.Actor;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.PoisonPill;
import akka.actor.Props;
import akka.japi.Pair;
import akka.stream.KillSwitch;
import akka.stream.KillSwitches;
import akka.stream.OverflowStrategy;
import akka.stream.UniqueKillSwitch;
import akka.stream.javadsl.Keep;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.stream.javadsl.SourceQueueWithComplete;
import akka.stream.testkit.TestSubscriber;
import akka.stream.testkit.javadsl.TestSink;
import akka.testkit.TestActor;
import akka.testkit.TestProbe;
import akka.testkit.javadsl.TestKit;

/**
 * Tests {@link org.eclipse.ditto.services.gateway.streaming.actors.StreamingSessionActor}.
 */
public final class StreamingSessionActorTest {

    @Rule
    public final TestName testName = new TestName();

    private final ActorSystem actorSystem;
    private final DittoProtocolSub mockSub;
    private final TestProbe commandRouterProbe;
    private final SourceQueueWithComplete<SessionedJsonifiable> sourceQueue;
    private final TestSubscriber.Probe<SessionedJsonifiable> sinkProbe;
    private final KillSwitch killSwitch;

    public StreamingSessionActorTest() {
        actorSystem = ActorSystem.create();
        mockSub = mock(DittoProtocolSub.class);
        when(mockSub.declareAcknowledgementLabels(any(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));
        commandRouterProbe = TestProbe.apply("commandRouter", actorSystem);
        final Sink<SessionedJsonifiable, TestSubscriber.Probe<SessionedJsonifiable>> sink =
                TestSink.probe(actorSystem);
        final Source<SessionedJsonifiable, SourceQueueWithComplete<SessionedJsonifiable>> source =
                Source.queue(100, OverflowStrategy.fail());
        final Pair<Pair<SourceQueueWithComplete<SessionedJsonifiable>, UniqueKillSwitch>,
                TestSubscriber.Probe<SessionedJsonifiable>> pair =
                source.viaMat(KillSwitches.single(), Keep.both()).toMat(sink, Keep.both()).run(actorSystem);
        sourceQueue = pair.first().first();
        sinkProbe = pair.second();
        killSwitch = pair.first().second();
    }

    @After
    public void cleanUp() {
        TestKit.shutdownActorSystem(actorSystem);
    }

    @Test
    public void terminateOnStreamFailure() {
        new TestKit(actorSystem) {{
            final ActorRef underTest = watch(actorSystem.actorOf(getProps()));
            killSwitch.abort(new IllegalStateException("expected exception"));
            expectTerminated(underTest);
        }};
    }

    @Test
    public void completeStreamWhenStopped() {
        new TestKit(actorSystem) {{
            final ActorRef underTest = watch(actorSystem.actorOf(getProps()));
            underTest.tell(PoisonPill.getInstance(), getRef());
            expectTerminated(underTest);
            sinkProbe.ensureSubscription();
            sinkProbe.expectComplete();
        }};
    }

    @Test
    public void terminateOnAckLabelDeclarationFailure() {
        onDeclareAckLabels(CompletableFuture.failedStage(AcknowledgementLabelNotUniqueException.getInstance()));
        new TestKit(actorSystem) {{
            final ActorRef underTest = watch(actorSystem.actorOf(getProps("ack")));
            expectTerminated(underTest);
        }};
    }

    @Test
    public void sendDeclaredAckForGlobalDispatching() {
        onDeclareAckLabels(CompletableFuture.completedStage(null));
        new TestKit(actorSystem) {{
            final ActorRef underTest = watch(actorSystem.actorOf(getProps("ack")));
            final Acknowledgement ack =
                    Acknowledgement.of(AcknowledgementLabel.of("ack"), ThingId.of("thing:id"), HttpStatusCode.OK,
                            DittoHeaders.newBuilder().correlationId("corr:" + testName.getMethodName()).build());
            underTest.tell(IncomingSignal.of(ack), ActorRef.noSender());
            commandRouterProbe.expectMsg(ack);
        }};
    }

    @Test
    public void sendMalformedAck() {
        onDeclareAckLabels(CompletableFuture.completedStage(null));
        new TestKit(actorSystem) {{
            final ActorRef underTest = watch(actorSystem.actorOf(getProps("ack")));
            final Acknowledgement ack =
                    Acknowledgement.of(AcknowledgementLabel.of("ack"), ThingId.of("thing:id"), HttpStatusCode.OK,
                            DittoHeaders.empty());
            underTest.tell(IncomingSignal.of(ack), ActorRef.noSender());
            final SessionedJsonifiable sessionedJsonifiable = sinkProbe.requestNext();
            assertThat(sessionedJsonifiable.getJsonifiable())
                    .isInstanceOf(AcknowledgementCorrelationIdMissingException.class);
        }};
    }

    @Test
    public void sendNonDeclaredAck() {
        onDeclareAckLabels(CompletableFuture.completedStage(null));
        new TestKit(actorSystem) {{
            final ActorRef underTest = watch(actorSystem.actorOf(getProps("ack")));
            final Acknowledgement ack =
                    Acknowledgement.of(AcknowledgementLabel.of("ack2"), ThingId.of("thing:id"), HttpStatusCode.OK,
                            DittoHeaders.empty());
            underTest.tell(IncomingSignal.of(ack), ActorRef.noSender());
            final SessionedJsonifiable sessionedJsonifiable = sinkProbe.requestNext();
            assertThat(sessionedJsonifiable.getJsonifiable())
                    .isInstanceOf(AcknowledgementLabelNotDeclaredException.class);
        }};
    }

    @Test
    public void acknowledgementRequestsAreRestrictedToDeclaredAcks() {
        onDeclareAckLabels(CompletableFuture.completedStage(null));
        setUpMockForTwinEventsSubscription();
        new TestKit(actorSystem) {{
            final ActorRef underTest = watch(actorSystem.actorOf(getProps("ack")));
            subscribeForTwinEvents(underTest);
            final Signal<?> signal = ThingDeleted.of(ThingId.of("thing:id"), 2L, DittoHeaders.newBuilder()
                    .correlationId("corr:" + testName.getMethodName())
                    .readGrantedSubjects(List.of(AuthorizationSubject.newInstance("ditto:ditto")))
                    .acknowledgementRequests(ackRequests("ack", "ack2"))
                    .build());
            underTest.tell(signal, ActorRef.noSender());

            final Signal<?> expectedSignal = signal.setDittoHeaders(signal.getDittoHeaders()
                    .toBuilder()
                    .acknowledgementRequests(ackRequests("ack"))
                    .build());
            assertThat(sinkProbe.requestNext().getJsonifiable()).isEqualTo(expectedSignal);
        }};
    }

    private Props getProps(final String... declaredAcks) {
        final Connect connect = getConnect(acks(declaredAcks));
        final AcknowledgementConfig acknowledgementConfig = DefaultAcknowledgementConfig.of(ConfigFactory.empty());
        final HeaderTranslator headerTranslator = HeaderTranslator.empty();
        final Props mockProps = Props.create(Actor.class, () -> new TestActor(new LinkedBlockingDeque<>()));
        final JwtValidator mockValidator = mock(JwtValidator.class);
        final JwtAuthenticationResultProvider mockProvider = mock(JwtAuthenticationResultProvider.class);
        return StreamingSessionActor.props(connect, mockSub, commandRouterProbe.ref(), acknowledgementConfig,
                headerTranslator, mockProps, mockValidator, mockProvider);
    }

    private void onDeclareAckLabels(final CompletionStage<Void> answer) {
        doAnswer(invocation -> answer).when(mockSub).declareAcknowledgementLabels(any(), any());
    }

    private void setUpMockForTwinEventsSubscription() {
        doAnswer(invocation -> CompletableFuture.completedStage(null))
                .when(mockSub)
                .subscribe(any(), any(), any());
    }

    private void subscribeForTwinEvents(final ActorRef underTest) {
        final AuthorizationContext authorizationContext =
                AuthorizationContext.newInstance(DittoAuthorizationContextType.PRE_AUTHENTICATED_HTTP,
                        AuthorizationSubject.newInstance("ditto:ditto"));
        underTest.tell(StartStreaming.getBuilder(StreamingType.EVENTS, testName.getMethodName(), authorizationContext)
                .build(), ActorRef.noSender());
        assertThat(sinkProbe.requestNext().getJsonifiable())
                .isEqualTo(new StreamingAck(StreamingType.EVENTS, true));
    }

    private Connect getConnect(final Set<AcknowledgementLabel> declaredAcks) {
        return new Connect(sourceQueue, testName.getMethodName(), "WS", JsonSchemaVersion.LATEST, null, declaredAcks);
    }

    private Set<AcknowledgementLabel> acks(final String... ackLabelNames) {
        return Arrays.stream(ackLabelNames).map(AcknowledgementLabel::of).collect(Collectors.toSet());
    }

    private Set<AcknowledgementRequest> ackRequests(final String... ackLabelNames) {
        return Arrays.stream(ackLabelNames)
                .map(AcknowledgementRequest::parseAcknowledgementRequest)
                .collect(Collectors.toSet());
    }
}
