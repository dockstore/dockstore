/*
 *    Copyright 2019 OICR
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package io.dockstore.webservice;

import java.util.Map;

import org.hibernate.HibernateException;
import org.hibernate.annotations.common.reflection.ReflectionManager;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.internal.MetadataImpl;
import org.hibernate.boot.registry.classloading.spi.ClassLoaderService;
import org.hibernate.engine.config.spi.ConfigurationService;
import org.hibernate.engine.spi.CascadeStyle;
import org.hibernate.engine.spi.CascadeStyles;
import org.hibernate.engine.spi.CascadingAction;
import org.hibernate.engine.spi.CascadingActions;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.event.service.spi.DuplicationStrategy;
import org.hibernate.event.service.spi.EventListenerGroup;
import org.hibernate.event.service.spi.EventListenerRegistry;
import org.hibernate.event.spi.EventType;
import org.hibernate.integrator.spi.Integrator;
import org.hibernate.jpa.AvailableSettings;
import org.hibernate.jpa.event.internal.core.HibernateEntityManagerEventListener;
import org.hibernate.jpa.event.internal.core.JpaDeleteEventListener;
import org.hibernate.jpa.event.internal.core.JpaFlushEntityEventListener;
import org.hibernate.jpa.event.internal.core.JpaMergeEventListener;
import org.hibernate.jpa.event.internal.core.JpaPersistEventListener;
import org.hibernate.jpa.event.internal.core.JpaPersistOnFlushEventListener;
import org.hibernate.jpa.event.internal.core.JpaPostDeleteEventListener;
import org.hibernate.jpa.event.internal.core.JpaPostInsertEventListener;
import org.hibernate.jpa.event.internal.core.JpaPostLoadEventListener;
import org.hibernate.jpa.event.internal.core.JpaPostUpdateEventListener;
import org.hibernate.jpa.event.internal.core.JpaSaveEventListener;
import org.hibernate.jpa.event.internal.core.JpaSaveOrUpdateEventListener;
import org.hibernate.jpa.event.internal.jpa.CallbackBuilderLegacyImpl;
import org.hibernate.jpa.event.internal.jpa.CallbackRegistryImpl;
import org.hibernate.jpa.event.spi.jpa.CallbackBuilder;
import org.hibernate.jpa.event.spi.jpa.CallbackRegistryConsumer;
import org.hibernate.jpa.event.spi.jpa.ListenerFactory;
import org.hibernate.jpa.event.spi.jpa.ListenerFactoryBuilder;
import org.hibernate.mapping.PersistentClass;
import org.hibernate.service.spi.ServiceRegistryImplementor;
import org.hibernate.service.spi.SessionFactoryServiceRegistry;

/**
 * Based on org.hibernate.jpa.event.spi.JpaIntegrator but that seems to modify too much behaviour, breaking things.
 * Comments in this file denote active code in the original file
 */
public class CustomJPAIntegrator implements Integrator {
    private static final DuplicationStrategy JPA_DUPLICATION_STRATEGY = new JPADuplicationStrategy();

    private ListenerFactory jpaListenerFactory;
    private CallbackBuilder callbackBuilder;
    private CallbackRegistryImpl callbackRegistry;
    private CascadeStyle oldPersistCascadeStyle;

    /**
     * Perform integration.
     *
     * @param metadata        The "compiled" representation of the mapping information
     * @param sessionFactory  The session factory being created
     * @param serviceRegistry The session factory's service registry
     */
    public void integrate(Metadata metadata, SessionFactoryImplementor sessionFactory, SessionFactoryServiceRegistry serviceRegistry) {

        // first, register the JPA-specific persist cascade style
        try {
            oldPersistCascadeStyle = CascadeStyles.getCascadeStyle("persist");
        } catch (Exception e) {
            assert true;
            /* do nothing from original source */
        }
        CascadeStyles.registerCascadeStyle("persist", new PersistCascadeStyle());

        // then prepare listeners
        final EventListenerRegistry eventListenerRegistry = serviceRegistry.getService(EventListenerRegistry.class);

        eventListenerRegistry.addDuplicationStrategy(JPA_DUPLICATION_STRATEGY);

        // op listeners
        //        eventListenerRegistry.setListeners( EventType.AUTO_FLUSH, JpaAutoFlushEventListener.INSTANCE );
        eventListenerRegistry.setListeners(EventType.DELETE, new JpaDeleteEventListener());
        eventListenerRegistry.setListeners(EventType.FLUSH_ENTITY, new JpaFlushEntityEventListener());
        // next line would break UserResourceIT.testSelfDestruct
        //        eventListenerRegistry.setListeners( EventType.FLUSH, JpaFlushEventListener.INSTANCE );
        eventListenerRegistry.setListeners(EventType.MERGE, new JpaMergeEventListener());
        eventListenerRegistry.setListeners(EventType.PERSIST, new JpaPersistEventListener());
        eventListenerRegistry.setListeners(EventType.PERSIST_ONFLUSH, new JpaPersistOnFlushEventListener());
        eventListenerRegistry.setListeners(EventType.SAVE, new JpaSaveEventListener());
        eventListenerRegistry.setListeners(EventType.SAVE_UPDATE, new JpaSaveOrUpdateEventListener());

        // post op listeners
        eventListenerRegistry.prependListeners(EventType.POST_DELETE, new JpaPostDeleteEventListener());
        eventListenerRegistry.prependListeners(EventType.POST_INSERT, new JpaPostInsertEventListener());
        eventListenerRegistry.prependListeners(EventType.POST_LOAD, new JpaPostLoadEventListener());
        eventListenerRegistry.prependListeners(EventType.POST_UPDATE, new JpaPostUpdateEventListener());

        final ConfigurationService cfgService = serviceRegistry.getService(ConfigurationService.class);

        for (Map.Entry entry : ((Map<?, ?>)cfgService.getSettings()).entrySet()) {
            if (!String.class.isInstance(entry.getKey())) {
                continue;
            }
            final String propertyName = (String)entry.getKey();
            if (!propertyName.startsWith(AvailableSettings.EVENT_LISTENER_PREFIX)) {
                continue;
            }
            final String eventTypeName = propertyName.substring(AvailableSettings.EVENT_LISTENER_PREFIX.length() + 1);
            final EventType eventType = EventType.resolveEventTypeByName(eventTypeName);
            final EventListenerGroup eventListenerGroup = eventListenerRegistry.getEventListenerGroup(eventType);
            for (String listenerImpl : ((String)entry.getValue()).split(" ,")) {
                eventListenerGroup.appendListener(instantiate(listenerImpl, serviceRegistry));
            }
        }

        // handle JPA "entity listener classes"...
        final ReflectionManager reflectionManager = ((MetadataImpl)metadata).getMetadataBuildingOptions().getReflectionManager();

        this.callbackRegistry = new CallbackRegistryImpl();
        this.jpaListenerFactory = ListenerFactoryBuilder.buildListenerFactory(sessionFactory.getSessionFactoryOptions());
        this.callbackBuilder = new CallbackBuilderLegacyImpl(jpaListenerFactory, reflectionManager);
        for (PersistentClass persistentClass : metadata.getEntityBindings()) {
            if (persistentClass.getClassName() == null) {
                // we can have non java class persisted by hibernate
                continue;
            }
            callbackBuilder.buildCallbacksForEntity(persistentClass.getClassName(), callbackRegistry);
        }

        for (EventType eventType : EventType.values()) {
            final EventListenerGroup eventListenerGroup = eventListenerRegistry.getEventListenerGroup(eventType);
            for (Object listener : eventListenerGroup.listeners()) {
                if (CallbackRegistryConsumer.class.isInstance(listener)) {
                    ((CallbackRegistryConsumer)listener).injectCallbackRegistry(callbackRegistry);
                }
            }
        }
    }

    @Override
    public void disintegrate(SessionFactoryImplementor sessionFactory, SessionFactoryServiceRegistry serviceRegistry) {
        if (oldPersistCascadeStyle == null) {
            CascadeStyles.registerCascadeStyle("persist", null);
        }
        CascadeStyles.registerCascadeStyle("persist", (CascadeStyles.BaseCascadeStyle)oldPersistCascadeStyle);

        if (callbackRegistry != null) {
            callbackRegistry.release();
        }
        if (callbackBuilder != null) {
            callbackBuilder.release();
        }
        if (jpaListenerFactory != null) {
            jpaListenerFactory.release();
        }
    }

    private Object instantiate(String listenerImpl, ServiceRegistryImplementor serviceRegistry) {
        try {
            return serviceRegistry.getService(ClassLoaderService.class).classForName(listenerImpl).newInstance();
        } catch (Exception e) {
            throw new HibernateException("Could not instantiate requested listener [" + listenerImpl + "]", e);
        }
    }

    private static class PersistCascadeStyle extends CascadeStyles.BaseCascadeStyle {
        @Override
        public boolean doCascade(CascadingAction action) {
            return action == JpaPersistEventListener.PERSIST_SKIPLAZY || action == CascadingActions.PERSIST_ON_FLUSH;
        }

        @Override
        public String toString() {
            return "STYLE_PERSIST_SKIPLAZY";
        }
    }

    private static class JPADuplicationStrategy implements DuplicationStrategy {
        @Override
        public boolean areMatch(Object listener, Object original) {
            return listener.getClass().equals(original.getClass()) && HibernateEntityManagerEventListener.class.isInstance(original);
        }

        @Override
        public Action getAction() {
            return Action.KEEP_ORIGINAL;
        }
    }
}
