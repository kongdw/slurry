package org.slurry.cache4guice.cache.util;

import java.util.List;

import net.sf.ehcache.Ehcache;

public interface Cache4GuiceHelper {

	public abstract List<Ehcache> findCaches(Class<? extends Object> searchClass);

}