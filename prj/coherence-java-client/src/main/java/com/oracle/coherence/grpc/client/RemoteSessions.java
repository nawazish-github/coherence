/*
 * Copyright (c) 2020, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License v 1.0 as shown at
 * http://oss.oracle.com/licenses/upl.
 */
package com.oracle.coherence.grpc.client;

import com.oracle.coherence.cdi.Remote;
import com.oracle.coherence.cdi.Scope;
import com.oracle.coherence.common.util.Options;

import com.tangosol.net.Session;
import com.tangosol.net.SessionProvider;

import io.helidon.config.Config;

import java.util.Iterator;
import java.util.Map;

import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.PreDestroy;

import javax.enterprise.context.ApplicationScoped;

import javax.enterprise.inject.Produces;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.CDI;
import javax.enterprise.inject.spi.InjectionPoint;

import javax.inject.Inject;

/**
 * A CDI producer for {@link GrpcRemoteSession} instances.
 *
 * @author Jonathan Knight  2019.11.28
 * @since 20.06
 */
@ApplicationScoped
public class RemoteSessions
        implements SessionProvider
    {
    // ----- constructors ---------------------------------------------------

    /**
     * Create a non-CDI {@link RemoteSessions}.
     */
    protected RemoteSessions()
        {
        this(null, Config.create());
        }

    /**
     * Create a {@link RemoteSessions}.
     *
     * @param beanManager the {@link javax.enterprise.inject.spi.BeanManager} to use
     */
    @Inject
    protected RemoteSessions(BeanManager beanManager, Config config)
        {
        f_beanManager = beanManager;
        f_config      = config == null ? Config.empty() : config;
        }

    // ----- SessionProvider interface --------------------------------------

    @Override
    public GrpcRemoteSession createSession(Session.Option... options)
        {
        Options<Session.Option> sessionOptions = Options.from(Session.Option.class, options);
        String sName = sessionOptions.get(NameOption.class, NameOption.DEFAULT).getName();
        String sScope = sessionOptions.get(ScopeOption.class, ScopeOption.DEFAULT).getScope();
        return ensureSession(sName, sScope);
        }

    // ----- public methods -------------------------------------------------

    /**
     * Obtain the singleton {@link RemoteSessions} instance.
     *
     * @return the singleton {@link RemoteSessions} instance
     */
    public static synchronized RemoteSessions instance()
        {
        if (s_instance == null)
            {
            try
                {
                s_instance = CDI.current()
                        .getBeanManager()
                        .createInstance()
                        .select(RemoteSessions.class)
                        .get();
                }
           catch (IllegalStateException ignored) // cdi not available
               {
               }

            if (s_instance == null)
                {
                s_instance = new RemoteSessions();
                }
            }
        return s_instance;
        }

    /**
     * Close all {@link GrpcRemoteSession} instances created
     * by the {@link RemoteSessions} factory.
     */
    @PreDestroy
    public void shutdown()
        {
        for (Map<String, GrpcRemoteSession> map : f_mapSessions.values())
            {
            Iterator<Map.Entry<String, GrpcRemoteSession>> iterator = map.entrySet().iterator();
            while (iterator.hasNext())
                {
                Map.Entry<String, GrpcRemoteSession> entry = iterator.next();
                try
                    {
                    entry.getValue().close();
                    }
                catch (Throwable t)
                    {
                    t.printStackTrace();
                    }
                iterator.remove();
                }
            }
        }

    /**
     * Obtain a {@link Session.Option} to specify the scope scope of the required {@link GrpcRemoteSession}.
     * <p>
     * A scope name of {@code null} will use the default scope name {@link Scope#DEFAULT}.
     *
     * @param scope  the scope name of the {@link GrpcRemoteSession}
     *
     * @return a {@link Session.Option} to specify the scope of the {@link GrpcRemoteSession}
     */
    public static Session.Option scope(String scope)
        {
        return scope == null ? ScopeOption.DEFAULT : new ScopeOption(scope);
        }

    // ----- helper methods -------------------------------------------------

    /**
     * Produces a remote {@link GrpcRemoteSession}.
     * <p>
     * If the value of the scope qualifier is blank or empty String the default
     * {@link Session} will be returned.
     *
     * @param injectionPoint the {@link InjectionPoint} that the cache factory it to be injected into
     *
     * @return the named {@link GrpcRemoteSession}
     */
    @Produces
    @Remote
    @Scope
    protected GrpcRemoteSession getSession(InjectionPoint injectionPoint)
        {
        String sName = injectionPoint.getQualifiers()
                .stream()
                .filter(q -> q.annotationType().isAssignableFrom(Remote.class))
                .map(q -> ((Remote) q).value().trim())
                .findFirst()
                .orElse(Remote.DEFAULT_NAME);

        String sScope = injectionPoint.getQualifiers()
                .stream()
                .filter(q -> q.annotationType().isAssignableFrom(Scope.class))
                .map(q -> ((Scope) q).value().trim())
                .findFirst()
                .orElse(Scope.DEFAULT);

        return ensureSession(sName, sScope);
        }

    /**
     * Obtain a {@link GrpcRemoteSession}, creating a new instance if required.
     *
     * @param sScope the scope name of the session
     *
     * @return a {@link GrpcRemoteSession} instance.
     */
    GrpcRemoteSession ensureSession(String sName, String sScope)
        {
        Map<String, GrpcRemoteSession> map     = f_mapSessions.computeIfAbsent(sScope, k -> new ConcurrentHashMap<>());
        GrpcRemoteSession              session = map.computeIfAbsent(sName, k -> GrpcRemoteSession.builder(f_config)
                                                    .name(sName)
                                                    .scope(sScope)
                                                    .beanManager(f_beanManager)
                                                    .build());

        if (session.isClosed())
            {
            // if the cached session has been closed then create a new session
            map.remove(sName);
            return ensureSession(sName, sScope);
            }

        return session;
        }

    // ----- inner class: NameOption ----------------------------------------

    /**
     * A {@link Session.Option} to use to specify the name for a session.
     */
    protected static class NameOption
            implements Session.Option
        {
        // ----- constructors -----------------------------------------------

        protected NameOption(String sName)
            {
            this.f_sName = sName;
            }

        /**
         * Return the name, or {@value Remote#DEFAULT_NAME} if {@code null}.
         *
         * @return the name, or {@value Remote#DEFAULT_NAME} if {@code null}.
         */
        protected String getName()
            {
            return f_sName == null || f_sName.isEmpty() ? DEFAULT.getName() : f_sName;
            }

        // ----- constants --------------------------------------------------

        /**
         * Default session name.
         */
        protected static final NameOption DEFAULT = new NameOption(Remote.DEFAULT_NAME);

        // ----- data members -----------------------------------------------

        /**
         * The remote session name.
         */
        protected final String f_sName;
        }

    /**
     * A {@link Session.Option} to use to specify the scope name for a session.
     */
    protected static class ScopeOption
            implements Session.Option
        {
        // ----- constructors -----------------------------------------------

        protected ScopeOption(String sScope)
            {
            f_sScope = sScope == null ? Scope.DEFAULT : sScope;
            }

        /**
         * Return the scope name.
         *
         * @return the scope name.
         */
        protected String getScope()
            {
            return f_sScope;
            }

        // ----- constants --------------------------------------------------

        /**
         * Default session name.
         */
        protected static final ScopeOption DEFAULT = new ScopeOption(Scope.DEFAULT);

        // ----- data members -----------------------------------------------

        /**
         * The remote session name.
         */
        protected final String f_sScope;
        }

    // ----- data members ---------------------------------------------------

    /**
     * A map of sessions stored by name.
     */
    protected final Map<String, Map<String, GrpcRemoteSession>> f_mapSessions = new ConcurrentHashMap<>();

    /**
     * The CDI {@link javax.enterprise.inject.spi.BeanManager}.
     */
    protected final BeanManager f_beanManager;

    /**
     * The default {@link Config} to use.
     */
    protected final Config f_config;

    /**
     * The singleton {@link RemoteSessions}.
     */
    protected static RemoteSessions s_instance;
    }
