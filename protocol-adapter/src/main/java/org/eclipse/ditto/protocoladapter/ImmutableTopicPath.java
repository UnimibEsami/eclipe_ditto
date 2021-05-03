/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.protocoladapter;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.text.MessageFormat;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.NotThreadSafe;

/**
 * Immutable implementation of {@link TopicPath}.
 */
@Immutable
final class ImmutableTopicPath implements TopicPath {

    private final String namespace;
    private final String name;
    private final Group group;
    private final Channel channel;
    private final Criterion criterion;
    @Nullable private final Action action;
    @Nullable private final SearchAction searchAction;
    @Nullable private final String subject;

    private ImmutableTopicPath(final Builder builder) {
        namespace = builder.namespace;
        name = builder.name;
        group = builder.group;
        channel = builder.channel;
        criterion = builder.criterion;
        action = builder.action;
        searchAction = builder.searchAction;
        subject = builder.subject;
    }

    /**
     * Returns a new builder with a fluent step API to create an {@code ImmutableTopicPath}.
     *
     * @param namespace the namespace part of the topic path to be built.
     * @param entityName the entity name part of the topic path to be built.
     * @return the builder.
     * @throws NullPointerException if any argument is {@code null}.
     */
    static TopicPathBuilder newBuilder(final String namespace, final String entityName) {
        checkNotNull(namespace, "namespace");
        checkNotNull(entityName, "entityName");
        return new Builder(namespace, entityName);
    }

    /**
     * Parses the string argument as a {@code ImmutableTopicPath}.
     *
     * @param topicPathString a String containing the ImmutableTopicPath representation to be parsed.
     * @return the {@code ImmutableTopicPath} represented by the argument.
     * @throws NullPointerException if {@code topicPathString} is {@code null}.
     * @throws UnknownTopicPathException if the string does not contain a parsable ImmutableTopicPath.
     */
    static ImmutableTopicPath parseTopicPath(final String topicPathString) {
        final TopicPathParser topicPathParser = new TopicPathParser(checkNotNull(topicPathString, "topicPathString"));
        return topicPathParser.get();
    }

    @Override
    public String getNamespace() {
        return namespace;
    }

    @Override
    public Group getGroup() {
        return group;
    }

    @Override
    public Channel getChannel() {
        return channel;
    }

    @Override
    public Criterion getCriterion() {
        return criterion;
    }

    @Override
    public Optional<Action> getAction() {
        return Optional.ofNullable(action);
    }

    @Override
    public Optional<SearchAction> getSearchAction() {
        return Optional.ofNullable(searchAction);
    }

    @Override
    public Optional<String> getSubject() {
        return Optional.ofNullable(subject);
    }

    @Override
    public String getEntityName() {
        return name;
    }

    @Override
    public String getPath() {
        final Stream<String> pathPartStream = Stream.<String>builder()
                .add(namespace)
                .add(name)
                .add(group.getName())
                .add(Channel.NONE != channel ? channel.getName() : null)
                .add(criterion.getName())
                .add(getStringOrNull(action))
                .add(getStringOrNull(searchAction))
                .add(getStringOrNull(subject))
                .build();
        return pathPartStream.filter(Objects::nonNull).collect(Collectors.joining(PATH_DELIMITER));
    }

    @Nullable
    private static String getStringOrNull(@Nullable final Object pathPart) {
        @Nullable final String result;
        if (null == pathPart) {
            result = null;
        } else {
            result = pathPart.toString();
        }
        return result;
    }

    @Override
    public boolean isGroup(@Nullable final Group expectedGroup) {
        return group.equals(expectedGroup);
    }

    @Override
    public boolean isChannel(@Nullable final Channel expectedChannel) {
        return channel.equals(expectedChannel);
    }

    @Override
    public boolean isCriterion(@Nullable final Criterion expectedCriterion) {
        return criterion.equals(expectedCriterion);
    }

    @Override
    public boolean isAction(@Nullable final Action expectedAction) {
        return Objects.equals(action, expectedAction);
    }

    @SuppressWarnings({"squid:MethodCyclomaticComplexity", "squid:S1067", "OverlyComplexMethod"})
    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ImmutableTopicPath that = (ImmutableTopicPath) o;
        return Objects.equals(namespace, that.namespace) &&
                Objects.equals(name, that.name) &&
                group == that.group &&
                channel == that.channel &&
                criterion == that.criterion &&
                Objects.equals(action, that.action) &&
                Objects.equals(searchAction, that.searchAction) &&
                Objects.equals(subject, that.subject);
    }

    @Override
    public int hashCode() {
        return Objects.hash(namespace, name, group, channel, criterion, action, searchAction, subject);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " ["
                + "namespace=" + namespace +
                ", id=" + name +
                ", group=" + group +
                ", channel=" + channel +
                ", criterion=" + criterion +
                ", action=" + action +
                ", searchAction=" + searchAction +
                ", subject=" + subject +
                ", path=" + getPath() +
                "]";
    }

    /**
     * Mutable implementation of {@link TopicPathBuilder} for building immutable {@link TopicPath} instances.
     */
    @NotThreadSafe
    private static final class Builder
            implements TopicPathBuilder, MessagesTopicPathBuilder, EventsTopicPathBuilder, CommandsTopicPathBuilder,
            AcknowledgementTopicPathBuilder, SearchTopicPathBuilder, AnnouncementsTopicPathBuilder {

        private final String namespace;
        private final String name;

        private Group group;
        private Channel channel;
        private Criterion criterion;
        @Nullable private Action action;
        @Nullable private SearchAction searchAction;
        @Nullable private String subject;

        private Builder(final String namespace, final String name) {
            this.namespace = namespace;
            this.name = name;
            group = null;
            channel = Channel.NONE;
            criterion = null;
            action = null;
            searchAction = null;
            subject = null;
        }

        @Override
        public TopicPathBuilder things() {
            group = Group.THINGS;
            return this;
        }

        @Override
        public TopicPathBuilder policies() {
            group = Group.POLICIES;
            return this;
        }

        @Override
        public TopicPathBuilder twin() {
            channel = Channel.TWIN;
            return this;
        }

        @Override
        public TopicPathBuilder live() {
            channel = Channel.LIVE;
            return this;
        }

        @Override
        public TopicPathBuilder none() {
            channel = Channel.NONE;
            return this;
        }

        @Override
        public SearchTopicPathBuilder search() {
            criterion = Criterion.SEARCH;
            return this;
        }

        @Override
        public CommandsTopicPathBuilder commands() {
            criterion = Criterion.COMMANDS;
            return this;
        }

        @Override
        public AnnouncementsTopicPathBuilder announcements() {
            criterion = Criterion.ANNOUNCEMENTS;
            return this;
        }

        @Override
        public EventsTopicPathBuilder events() {
            criterion = Criterion.EVENTS;
            return this;
        }

        @Override
        public TopicPathBuildable errors() {
            criterion = Criterion.ERRORS;
            return this;
        }

        @Override
        public MessagesTopicPathBuilder messages() {
            criterion = Criterion.MESSAGES;
            return this;
        }

        @Override
        public AcknowledgementTopicPathBuilder acks() {
            criterion = Criterion.ACKS;
            return this;
        }

        @Override
        public CommandsTopicPathBuilder create() {
            action = Action.CREATE;
            return this;
        }

        @Override
        public CommandsTopicPathBuilder retrieve() {
            action = Action.RETRIEVE;
            return this;
        }

        @Override
        public CommandsTopicPathBuilder modify() {
            action = Action.MODIFY;
            return this;
        }

        @Override
        public CommandsTopicPathBuilder merge() {
            action = Action.MERGE;
            return this;
        }

        @Override
        public CommandsTopicPathBuilder delete() {
            action = Action.DELETE;
            return this;
        }

        @Override
        public TopicPathBuildable subscribe() {
            searchAction = SearchAction.SUBSCRIBE;
            return this;
        }

        @Override
        public TopicPathBuildable cancel() {
            searchAction = SearchAction.CANCEL;
            return this;
        }

        @Override
        public TopicPathBuildable request() {
            searchAction = SearchAction.REQUEST;
            return this;
        }

        @Override
        public TopicPathBuildable complete() {
            searchAction = SearchAction.COMPLETE;
            return this;
        }

        @Override
        public TopicPathBuildable failed() {
            searchAction = SearchAction.FAILED;
            return this;
        }

        @Override
        public TopicPathBuildable hasNext() {
            searchAction = SearchAction.NEXT;
            return this;
        }

        @Override
        public EventsTopicPathBuilder generated() {
            searchAction = SearchAction.GENERATED;
            return this;
        }

        @Override
        public TopicPathBuildable error() {
            searchAction = SearchAction.ERROR;
            return this;
        }

        @Override
        public EventsTopicPathBuilder created() {
            action = Action.CREATED;
            return this;
        }

        @Override
        public EventsTopicPathBuilder modified() {
            action = Action.MODIFIED;
            return this;
        }

        @Override
        public EventsTopicPathBuilder merged() {
            action = Action.MERGED;
            return this;
        }

        @Override
        public EventsTopicPathBuilder deleted() {
            action = Action.DELETED;
            return this;
        }

        @Override
        public MessagesTopicPathBuilder subject(final String subject) {
            this.subject = checkNotNull(subject, "subject");
            return this;
        }

        @Override
        public AnnouncementsTopicPathBuilder name(final String name) {
            subject = checkNotNull(name, "name");
            return this;
        }

        @Override
        public AcknowledgementTopicPathBuilder label(final CharSequence label) {
            subject = checkNotNull(label, "label").toString();
            return this;
        }

        @Override
        public AcknowledgementTopicPathBuilder aggregatedAcks() {
            subject = null;
            return this;
        }

        @Override
        public ImmutableTopicPath build() {
            validateChannel();
            return new ImmutableTopicPath(this);
        }

        private void validateChannel() {
            if (Group.POLICIES == group && Channel.NONE != channel) {
                throw new IllegalStateException("The policies group requires no channel.");
            }
        }

    }

    @NotThreadSafe
    private static final class TopicPathParser implements Supplier<ImmutableTopicPath> {

        private final String topicPathString;
        private final LinkedList<String> topicPathParts;

        private TopicPathParser(final String topicPathString) {
            this.topicPathString = topicPathString;
            topicPathParts = splitByPathDelimiter(topicPathString);
        }

        private static LinkedList<String> splitByPathDelimiter(final String topicPathString) {
            final LinkedList<String> result;
            if (topicPathString.isEmpty()) {
                result = new LinkedList<>();
            } else {
                result = new LinkedList<>(Arrays.asList(topicPathString.split(TopicPath.PATH_DELIMITER)));
            }
            return result;
        }

        @Override
        public ImmutableTopicPath get() {
            final Builder topicPathBuilder = new Builder(tryToGetNamespace(), tryToGetEntityName());
            topicPathBuilder.group = tryToGetGroup(tryToGetGroupName());
            topicPathBuilder.channel = tryToGetChannelForGroup(topicPathBuilder.group);
            topicPathBuilder.criterion = tryToGetCriterion(tryToGetCriterionName());
            switch (topicPathBuilder.criterion) {
                case COMMANDS:
                case EVENTS:
                    topicPathBuilder.action = tryToGetActionForName(tryToGetActionName());
                    break;
                case SEARCH:
                    topicPathBuilder.searchAction = tryToGetSearchActionForName(tryToGetSearchActionName());
                    break;
                case ERRORS:
                    break;
                case MESSAGES:
                case ACKS:
                case ANNOUNCEMENTS:
                    topicPathBuilder.subject = getSubjectOrNull();
                    break;
                default:
                    throw UnknownTopicPathException.newBuilder(topicPathString)
                            .description(MessageFormat.format("Criterion <{0}> is unhandled.",
                                    topicPathBuilder.criterion))
                            .build();
            }
            return topicPathBuilder.build();
        }

        private String tryToGetNamespace() {
            try {
                return topicPathParts.pop(); // parts[0]
            } catch (final NoSuchElementException e) {
                throw UnknownTopicPathException.newBuilder(topicPathString)
                        .description("The topic path has no namespace part.")
                        .cause(e)
                        .build();
            }
        }

        private String tryToGetEntityName() {
            try {
                return topicPathParts.pop(); // parts[1]
            } catch (final NoSuchElementException e) {
                throw UnknownTopicPathException.newBuilder(topicPathString)
                        .description("The topic path has no entity name part.")
                        .cause(e)
                        .build();
            }
        }

        private String tryToGetGroupName() {
            try {
                return topicPathParts.pop(); // parts[2]
            } catch (final NoSuchElementException e) {
                throw UnknownTopicPathException.newBuilder(topicPathString)
                        .description("The topic path has no group part.")
                        .cause(e)
                        .build();
            }
        }

        private Group tryToGetGroup(final String groupName) {
            return Group.forName(groupName).orElseThrow(() -> UnknownTopicPathException.newBuilder(topicPathString)
                    .description(MessageFormat.format("Group name <{0}> is unknown.", groupName))
                    .build());
        }

        private Channel tryToGetChannelForGroup(final Group group) {
            final Channel result;
            if (Group.POLICIES == group) {
                result = Channel.NONE;
            } else {
                result = tryToGetChannelForName(tryToGetChannelName());
            }
            return result;
        }

        private String tryToGetChannelName() {
            try {
                return topicPathParts.pop(); // parts[3]
            } catch (final NoSuchElementException e) {
                throw UnknownTopicPathException.newBuilder(topicPathString)
                        .description("The topic path has no channel part.")
                        .cause(e)
                        .build();
            }
        }

        private Channel tryToGetChannelForName(final String channelName) {
            return Channel.forName(channelName).orElseThrow(() -> UnknownTopicPathException.newBuilder(topicPathString)
                    .description(MessageFormat.format("Channel name <{0}> is unknown.", channelName))
                    .build());
        }

        private String tryToGetCriterionName() {
            try {
                return topicPathParts.pop(); // parts[4]
            } catch (final NoSuchElementException e) {
                throw UnknownTopicPathException.newBuilder(topicPathString)
                        .description("The topic path has no criterion part.")
                        .cause(e)
                        .build();
            }
        }

        private Criterion tryToGetCriterion(final String criterionName) {
            return Criterion.forName(criterionName)
                    .orElseThrow(() -> UnknownTopicPathException.newBuilder(topicPathString)
                            .description(MessageFormat.format("Criterion name <{0}> is unknown.", criterionName))
                            .build());
        }

        private String tryToGetActionName() {
            try {
                return topicPathParts.pop(); // parts[5]
            } catch (final NoSuchElementException e) {
                throw UnknownTopicPathException.newBuilder(topicPathString)
                        .description("The topic path has no action part.")
                        .cause(e)
                        .build();
            }
        }

        private Action tryToGetActionForName(final String actionName) {
            return Action.forName(actionName).orElseThrow(() -> UnknownTopicPathException.newBuilder(topicPathString)
                    .description(MessageFormat.format("Action name <{0}> is unknown.", actionName))
                    .build());
        }

        private String tryToGetSearchActionName() {
            try {
                return topicPathParts.pop(); // parts[5]
            } catch (final NoSuchElementException e) {
                throw UnknownTopicPathException.newBuilder(topicPathString)
                        .description("The topic path has no search action part.")
                        .cause(e)
                        .build();
            }
        }

        private SearchAction tryToGetSearchActionForName(final String searchActionName) {
            return SearchAction.forName(searchActionName)
                    .orElseThrow(() -> UnknownTopicPathException.newBuilder(topicPathString)
                            .description(MessageFormat.format("Search action name <{0}> is unknown.", searchActionName))
                            .build());
        }

        @Nullable
        private String getSubjectOrNull() {
            final String subject = String.join(TopicPath.PATH_DELIMITER, topicPathParts);
            final String result;
            if (subject.isEmpty()) {
                result = null;
            } else {
                result = subject;
            }
            return result;
        }

    }

}
