package info.archinnov.achilles.entity.manager;

import info.archinnov.achilles.context.ConfigurationContext;
import info.archinnov.achilles.context.ThriftDaoContext;
import info.archinnov.achilles.context.ThriftImmediateFlushContext;
import info.archinnov.achilles.context.ThriftPersistenceContext;
import info.archinnov.achilles.context.execution.ThriftSafeExecutionContext;
import info.archinnov.achilles.entity.metadata.EntityMeta;
import info.archinnov.achilles.entity.operations.EntityRefresher;
import info.archinnov.achilles.entity.operations.EntityValidator;
import info.archinnov.achilles.entity.operations.ThriftEntityLoader;
import info.archinnov.achilles.entity.operations.ThriftEntityMerger;
import info.archinnov.achilles.entity.operations.ThriftEntityPersister;
import info.archinnov.achilles.entity.operations.ThriftEntityProxifier;
import info.archinnov.achilles.type.ConsistencyLevel;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ThriftEntityManager
 * 
 * Thrift-based Entity Manager for Achilles. This entity manager is perfectly thread-safe and
 * 
 * can be used as a singleton. Entity state is stored in proxy object, which is obviously not
 * 
 * thread-safe.
 * 
 * Internally the ThriftEntityManager relies on Hector API for common operations
 * 
 * @author DuyHai DOAN
 * 
 */
public class ThriftEntityManager extends AchillesEntityManager<ThriftPersistenceContext> {
    private static final Logger log = LoggerFactory.getLogger(ThriftEntityManager.class);

    protected ThriftDaoContext thriftDaoContext;

    ThriftEntityManager(AchillesEntityManagerFactory entityManagerFactory, Map<Class<?>, EntityMeta> entityMetaMap, //
            ThriftDaoContext thriftDaoContext, //
            ConfigurationContext configContext) {
        super(entityManagerFactory, entityMetaMap, configContext);
        this.thriftDaoContext = thriftDaoContext;
        super.persister = new ThriftEntityPersister();
        super.loader = new ThriftEntityLoader();
        super.merger = new ThriftEntityMerger();
        super.proxifier = new ThriftEntityProxifier();
        super.entityValidator = new EntityValidator<ThriftPersistenceContext>(super.proxifier);
        super.refresher = new EntityRefresher<ThriftPersistenceContext>(super.loader, super.proxifier);
    }

    @Override
    public void persist(final Object entity, ConsistencyLevel writeLevel) {
        log.debug("Persisting entity '{}' with write consistency level {}", entity, writeLevel.name());
        consistencyPolicy.setCurrentWriteLevel(writeLevel);
        reinitConsistencyLevels(new ThriftSafeExecutionContext<Void>() {
            @Override
            public Void execute() {
                persist(entity);
                return null;
            }
        });
    }

    @Override
    public <T> T merge(final T entity, ConsistencyLevel writeLevel) {
        if (log.isDebugEnabled()) {
            log.debug("Merging entity '{}' with write consistency level {}", proxifier.unproxy(entity),
                    writeLevel.name());
        }
        consistencyPolicy.setCurrentWriteLevel(writeLevel);
        return reinitConsistencyLevels(new ThriftSafeExecutionContext<T>() {
            @Override
            public T execute() {
                return merge(entity);
            }
        });
    }

    @Override
    public void remove(final Object entity, ConsistencyLevel writeLevel) {
        if (log.isDebugEnabled()) {
            log.debug("Removing entity '{}' with write consistency level {}", proxifier.unproxy(entity),
                    writeLevel.name());
        }
        consistencyPolicy.setCurrentWriteLevel(writeLevel);
        reinitConsistencyLevels(new ThriftSafeExecutionContext<Void>() {
            @Override
            public Void execute() {
                remove(entity);
                return null;
            }
        });

    }

    @Override
    public <T> T find(final Class<T> entityClass, final Object primaryKey, ConsistencyLevel readLevel) {

        log.debug("Find entity class '{}' with primary key {} and read consistency level {}", entityClass,
                primaryKey, readLevel.name());

        consistencyPolicy.setCurrentReadLevel(readLevel);
        return reinitConsistencyLevels(new ThriftSafeExecutionContext<T>() {
            @Override
            public T execute() {
                return find(entityClass, primaryKey);
            }
        });
    }

    @Override
    public <T> T getReference(final Class<T> entityClass, final Object primaryKey, ConsistencyLevel readLevel) {
        log.debug("Get reference for entity class '{}' with primary key {} and read consistency level {}",
                entityClass, primaryKey, readLevel.name());

        consistencyPolicy.setCurrentReadLevel(readLevel);
        return reinitConsistencyLevels(new ThriftSafeExecutionContext<T>() {
            @Override
            public T execute() {
                return getReference(entityClass, primaryKey);
            }
        });
    }

    @Override
    public void refresh(final Object entity, ConsistencyLevel readLevel) {
        if (log.isDebugEnabled()) {
            log.debug("Refreshing entity '{}' with read consistency level {}", proxifier.unproxy(entity),
                    readLevel.name());
        }
        consistencyPolicy.setCurrentReadLevel(readLevel);
        reinitConsistencyLevels(new ThriftSafeExecutionContext<Void>() {
            @Override
            public Void execute() {
                refresh(entity);
                return null;
            }
        });
    }

    /**
     * Create a new state-full EntityManager for batch handling <br/>
     * <br/>
     * 
     * <strong>WARNING : This EntityManager is state-full and not thread-safe. In case of exception, you MUST not
     * re-use it but create another one</strong>
     * 
     * @return a new state-full EntityManager
     */
    public ThriftBatchingEntityManager batchingEntityManager() {
        return new ThriftBatchingEntityManager(entityManagerFactory, entityMetaMap, thriftDaoContext, configContext);
    }

    @Override
    protected ThriftPersistenceContext initPersistenceContext(Class<?> entityClass, Object primaryKey) {
        log.trace("Initializing new persistence context for entity class {} and primary key {}",
                entityClass.getCanonicalName(), primaryKey);

        EntityMeta entityMeta = this.entityMetaMap.get(entityClass);
        return new ThriftPersistenceContext(entityMeta, configContext, thriftDaoContext,
                new ThriftImmediateFlushContext(thriftDaoContext, consistencyPolicy), entityClass, primaryKey);
    }

    @Override
    protected ThriftPersistenceContext initPersistenceContext(Object entity) {
        log.trace("Initializing new persistence context for entity {}", entity);

        EntityMeta entityMeta = this.entityMetaMap.get(proxifier.deriveBaseClass(entity));
        return new ThriftPersistenceContext(entityMeta, configContext, thriftDaoContext,
                new ThriftImmediateFlushContext(thriftDaoContext, consistencyPolicy), entity);
    }

    private <T> T reinitConsistencyLevels(ThriftSafeExecutionContext<T> context) {
        try {
            return context.execute();
        } finally {
            consistencyPolicy.reinitCurrentConsistencyLevels();
            consistencyPolicy.reinitDefaultConsistencyLevels();
        }
    }

    protected void setThriftDaoContext(ThriftDaoContext thriftDaoContext) {
        this.thriftDaoContext = thriftDaoContext;
    }
}
