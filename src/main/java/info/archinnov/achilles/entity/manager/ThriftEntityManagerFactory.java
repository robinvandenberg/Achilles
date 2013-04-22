package info.archinnov.achilles.entity.manager;

import info.archinnov.achilles.columnFamily.ThriftColumnFamilyCreator;
import info.archinnov.achilles.consistency.AchillesConfigurableConsistencyLevelPolicy;
import info.archinnov.achilles.dao.CounterDao;
import info.archinnov.achilles.dao.GenericColumnFamilyDao;
import info.archinnov.achilles.dao.GenericEntityDao;
import info.archinnov.achilles.entity.metadata.EntityMeta;
import info.archinnov.achilles.entity.metadata.PropertyMeta;
import info.archinnov.achilles.entity.parser.EntityExplorer;
import info.archinnov.achilles.entity.parser.EntityParser;
import info.archinnov.achilles.entity.parser.context.EntityParsingContext;
import info.archinnov.achilles.entity.parser.validator.EntityParsingValidator;
import info.archinnov.achilles.entity.type.ConsistencyLevel;
import info.archinnov.achilles.exception.AchillesException;
import info.archinnov.achilles.json.ObjectMapperFactory;
import info.archinnov.achilles.validation.Validator;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.persistence.EntityManager;

import me.prettyprint.hector.api.Cluster;
import me.prettyprint.hector.api.HConsistencyLevel;
import me.prettyprint.hector.api.Keyspace;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ThriftEntityManagerFactoryImpl
 * 
 * @author DuyHai DOAN
 * 
 */
public class ThriftEntityManagerFactory implements AchillesEntityManagerFactory
{

	private static final Logger log = LoggerFactory.getLogger(ThriftEntityManagerFactory.class);

	private List<String> entityPackages;
	private Cluster cluster;
	private Keyspace keyspace;
	private ThriftColumnFamilyCreator thriftColumnFamilyCreator;
	private ObjectMapperFactory objectMapperFactory;

	private EntityParser entityParser = new EntityParser();
	private EntityExplorer entityExplorer = new EntityExplorer();
	private EntityParsingValidator validator = new EntityParsingValidator();

	boolean forceColumnFamilyCreation = false;
	private CounterDao counterDao;
	private AchillesConfigurableConsistencyLevelPolicy consistencyPolicy;

	private Map<Class<?>, EntityMeta<?>> entityMetaMap = new HashMap<Class<?>, EntityMeta<?>>();
	private Map<String, GenericEntityDao<?>> entityDaosMap = new HashMap<String, GenericEntityDao<?>>();
	private Map<String, GenericColumnFamilyDao<?, ?>> columnFamilyDaosMap = new HashMap<String, GenericColumnFamilyDao<?, ?>>();

	private ArgumentExtractorForThriftEMF argumentExtractor = new ArgumentExtractorForThriftEMF();

	protected ThriftEntityManagerFactory() {
		counterDao = null;
	}

	/**
	 * Create a new ThriftEntityManagerFactoryImpl with a configuration map
	 * 
	 * @param configurationMap
	 * 
	 *            The configurationMap accepts the following properties:<br/>
	 *            <hr/>
	 *            <h1>Entity Packages</h1>
	 *            <br/>
	 *            <ul>
	 *            <li>"achilles.entity.packages" <strong>(MANDATORY)</strong>: list of java packages for entity scanning, separated by comma.<br/>
	 *            <br/>
	 *            &nbsp&nbspExample: <strong>"my.project.entity,another.project.entity"</strong></li>
	 *            </ul>
	 *            <hr/>
	 *            <h1>Cluster and Keyspace</h1>
	 *            <br/>
	 *            <ul>
	 *            <li>"achilles.cassandra.host": hostname/port of the Cassandra cluster <br/>
	 *            <br/>
	 *            &nbsp&nbspExample: <strong>localhost:9160</strong> <br/>
	 *            <br/>
	 *            </li>
	 *            <li>"achilles.cassandra.cluster.name": Cassandra cluster name</li>
	 *            <li>"achilles.cassandra.keyspace.name": Cassandra keyspace name</li>
	 *            </ul>
	 *            <ul>
	 *            <li>"achilles.cassandra.cluster": instance of pre-configured <em>me.prettyprint.hector.api.Cluster</em> object from Hector API</li>
	 *            <li>"achilles.cassandra.keyspace": instance of pre-configured <em>me.prettyprint.hector.api.Keyspace</em> object from Hector API</li>
	 *            </ul>
	 * 
	 *            <strong>Either "achilles.cassandra.cluster" or "achilles.cassandra.host"/"achilles.cassandra.cluster.name" parameters should be provided</strong> <br/>
	 *            <br/>
	 *            <strong>Either "achilles.cassandra.keyspace" or "achilles.cassandra.keyspace.name" parameters should be provided</strong>
	 * 
	 *            <hr/>
	 *            <h1>DDL Parameter</h1>
	 *            <br/>
	 *            <ul>
	 *            <li>"achilles.ddl.force.column.family.creation" <strong>(OPTIONAL)</strong>: create missing column families for entities if they are not found. Default = 'false'.<br/>
	 *            <br/>
	 *            &nbsp;&nbsp;If "achilles.ddl.force.column.family.creation" = false and no column family is found for any entity, Achilles will raise an <strong>InvalidColumnFamilyException</strong><br/>
	 *            <br/>
	 *            </li>
	 *            </ul>
	 *            <hr/>
	 *            <h1>JSON Serialization</h1>
	 *            <br/>
	 *            <ul>
	 *            <li>"achilles.json.object.mapper.factory" <strong>(OPTIONAL)</strong>: an implementation of the <em>info.archinnov.achilles.json.ObjectMapperFactory</em> interface to build custom
	 *            <strong>Jackson ObjectMapper</strong> based on entity class<br/>
	 *            <br/>
	 *            </li>
	 *            <li>achilles.json.object.mapper <strong>(OPTIONAL)</strong>: default <strong>Jackson ObjectMapper</strong> to use for serializing entities<br/>
	 *            <br/>
	 *            &nbsp;&nbsp;If both "achilles.json.object.mapper.factory" and "achilles.json.object.mapper" parameters are provided, Achilles will ignore the "achilles.json.object.mapper" value and
	 *            use the "achilles.json.object.mapper.factory"<br/>
	 *            <br/>
	 *            If none is provided, Achilles will use a default <strong>Jackson ObjectMapper</strong> with the following configuration:<br/>
	 *            <br/>
	 *            <ol>
	 *            <li>SerializationInclusion = Inclusion.NON_NULL</li>
	 *            <li>DeserializationConfig.Feature.FAIL_ON_UNKNOWN_PROPERTIES = false</li>
	 *            <li>AnnotationIntrospector pair : primary = <strong>JacksonAnnotationIntrospector</strong>, secondary = <strong>JaxbAnnotationIntrospector</strong></li>
	 *            </ol>
	 *            </li>
	 *            </ul>
	 *            <hr/>
	 *            <h1>Consistency Level</h1>
	 *            <br/>
	 *            <ul>
	 *            <li>"achilles.default.consistency.read" <strong>(OPTIONAL)</strong>: default read consistency level for all entities<br/>
	 *            <br/>
	 *            </li>
	 *            <li>"achilles.default.consistency.write" <strong>(OPTIONAL)</strong>: default write consistency level for all entities<br/>
	 *            <br/>
	 *            </li>
	 *            <li>"achilles.consistency.read.map" <strong>(OPTIONAL)</strong>: map(String,String) of read consistency levels for column families<br/>
	 *            <br/>
	 *            &nbsp;&nbsp;Example:<br/>
	 *            <br/>
	 *            &nbsp;&nbsp;&nbsp;&nbsp;"columnFamily1" -> "ONE"<br/>
	 *            &nbsp;&nbsp;&nbsp;&nbsp;"columnFamily2" -> "QUORUM"<br/>
	 *            &nbsp;&nbsp;&nbsp;&nbsp;...<br/>
	 *            <br/>
	 *            </li>
	 *            <li>"achilles.consistency.write.map" <strong>(OPTIONAL)</strong>: map(String,String) of write consistency levels for column families<br/>
	 *            <br/>
	 *            &nbsp;&nbsp;Example:<br/>
	 *            <br/>
	 *            &nbsp;&nbsp;&nbsp;&nbsp;"columnFamily1" -> "ALL"<br/>
	 *            &nbsp;&nbsp;&nbsp;&nbsp;"columnFamily2" -> "EACH_QUORUM"<br/>
	 *            &nbsp;&nbsp;&nbsp;&nbsp;...<br/>
	 *            <br/>
	 *            </li>
	 *            </ul>
	 * 
	 * 
	 */
	public ThriftEntityManagerFactory(Map<String, Object> configurationMap) {
		Validator.validateNotNull(configurationMap,
				"Configuration map for Achilles ThrifEntityManagerFactory should not be null");
		Validator.validateNotEmpty(configurationMap,
				"Configuration map for Achilles ThrifEntityManagerFactory should not be empty");

		entityPackages = argumentExtractor.initEntityPackages(configurationMap);
		cluster = argumentExtractor.initCluster(configurationMap);
		consistencyPolicy = initConsistencyLevelPolicy(configurationMap);
		keyspace = argumentExtractor.initKeyspace(cluster, consistencyPolicy, configurationMap);
		forceColumnFamilyCreation = argumentExtractor.initForceCFCreation(configurationMap);
		objectMapperFactory = argumentExtractor.initObjectMapperFactory(configurationMap);

		log.info(
				"Initializing Achilles ThriftEntityManagerFactory for cluster '{}' and keyspace '{}' ",
				cluster.getName(), keyspace.getKeyspaceName());

		thriftColumnFamilyCreator = new ThriftColumnFamilyCreator(cluster, keyspace);
		counterDao = new CounterDao(cluster, keyspace, consistencyPolicy);
		keyspace.setConsistencyLevelPolicy(consistencyPolicy);
		bootstrap();
	}

	protected void bootstrap()
	{
		log.info("Bootstraping Achilles Thrift-based EntityManagerFactory ");

		boolean createCounterCf = false;
		try
		{
			createCounterCf = discoverEntities();
		}
		catch (Exception e)
		{
			throw new AchillesException("Exception during entity parsing : " + e.getMessage(), e);
		}

		thriftColumnFamilyCreator.validateOrCreateColumnFamilies(entityMetaMap,
				forceColumnFamilyCreation, createCounterCf);
	}

	protected boolean discoverEntities() throws ClassNotFoundException, IOException
	{
		log.info("Start discovery of entities, searching in packages '{}'",
				StringUtils.join(entityPackages, ","));
		Map<PropertyMeta<?, ?>, Class<?>> joinPropertyMetaToBeFilled = new HashMap<PropertyMeta<?, ?>, Class<?>>();

		List<Class<?>> entities = entityExplorer.discoverEntities(entityPackages);
		validator.validateAtLeastOneEntity(entities, entityPackages);
		boolean createCounterCf = false;
		for (Class<?> entityClass : entities)
		{
			EntityParsingContext context = new EntityParsingContext(//
					joinPropertyMetaToBeFilled, //
					entityDaosMap, //
					columnFamilyDaosMap, //
					consistencyPolicy, //
					cluster, keyspace, //
					objectMapperFactory, entityClass);

			EntityMeta<?> entityMeta = entityParser.parseEntity(context);
			entityMetaMap.put(entityClass, entityMeta);
			createCounterCf = context.getHasCounter() || createCounterCf;
		}

		entityParser.fillJoinEntityMeta(new EntityParsingContext( //
				joinPropertyMetaToBeFilled, //
				columnFamilyDaosMap, //
				consistencyPolicy, //
				cluster, keyspace), entityMetaMap);

		return createCounterCf;
	}

	/**
	 * Create a new ThriftEntityManager
	 * 
	 * @return ThriftEntityManager
	 */
	@Override
	public EntityManager createEntityManager()
	{
		log.info("Create new Thrift-based Entity Manager ");

		return new ThriftEntityManager(Collections.unmodifiableMap(entityMetaMap), //
				Collections.unmodifiableMap(entityDaosMap), //
				Collections.unmodifiableMap(columnFamilyDaosMap), //
				counterDao, consistencyPolicy);
	}

	/**
	 * Create a new ThriftEntityManager
	 * 
	 * @return ThriftEntityManager
	 */
	@Override
	public EntityManager createEntityManager(@SuppressWarnings("rawtypes") Map map)
	{
		log.info("Create new Thrift-based Entity Manager ");

		return new ThriftEntityManager(Collections.unmodifiableMap(entityMetaMap), //
				Collections.unmodifiableMap(entityDaosMap), //
				Collections.unmodifiableMap(columnFamilyDaosMap), //
				counterDao, consistencyPolicy);
	}

	/**
	 * Not supported operation. Will throw UnsupportedOperationException
	 */
	@Override
	public void close()
	{
		throw new UnsupportedOperationException("This operation is not supported for Cassandra");
	}

	/**
	 * Not supported operation. Will throw UnsupportedOperationException
	 */
	@Override
	public boolean isOpen()
	{
		throw new UnsupportedOperationException("This operation is not supported for Cassandra");
	}

	protected AchillesConfigurableConsistencyLevelPolicy initConsistencyLevelPolicy(
			Map<String, Object> configurationMap)
	{
		log.info("Initializing new Achilles Configurable Consistency Level Policy from arguments ");

		ConsistencyLevel defaultReadConsistencyLevel = argumentExtractor
				.initDefaultReadConsistencyLevel(configurationMap);
		ConsistencyLevel defaultWriteConsistencyLevel = argumentExtractor
				.initDefaultWriteConsistencyLevel(configurationMap);
		Map<String, HConsistencyLevel> readConsistencyMap = argumentExtractor
				.initReadConsistencyMap(configurationMap);
		Map<String, HConsistencyLevel> writeConsistencyMap = argumentExtractor
				.initWriteConsistencyMap(configurationMap);

		return new AchillesConfigurableConsistencyLevelPolicy(defaultReadConsistencyLevel,
				defaultWriteConsistencyLevel, readConsistencyMap, writeConsistencyMap);
	}
}
