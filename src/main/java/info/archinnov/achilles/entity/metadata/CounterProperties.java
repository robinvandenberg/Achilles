package info.archinnov.achilles.entity.metadata;


/**
 * CounterProperties
 * 
 * @author DuyHai DOAN
 * 
 */
public class CounterProperties
{
	private String fqcn;
	private PropertyMeta<Void, ?> idMeta;

	public CounterProperties(String fqcn) {
		this.fqcn = fqcn;
	}

	public CounterProperties(String fqcn, PropertyMeta<Void, ?> idMeta) {
		this.fqcn = fqcn;
		this.idMeta = idMeta;
	}

	public String getFqcn()
	{
		return fqcn;
	}

	public void setFqcn(String fqcn)
	{
		this.fqcn = fqcn;
	}

	public PropertyMeta<Void, ?> getIdMeta()
	{
		return idMeta;
	}

	public void setIdMeta(PropertyMeta<Void, ?> idMeta)
	{
		this.idMeta = idMeta;
	}
}