package com.sensei.search.nodes;

import java.io.File;
import java.io.FilenameFilter;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.management.InstanceAlreadyExistsException;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.log4j.Logger;

import proj.zoie.api.IndexReaderFactory;
import proj.zoie.api.Zoie;
import proj.zoie.api.ZoieIndexReader;

import com.browseengine.bobo.api.BoboIndexReader;
import com.linkedin.norbert.javacompat.cluster.ClusterClient;
import com.linkedin.norbert.javacompat.network.NetworkServer;
import com.sensei.search.svc.api.SenseiException;

public class SenseiCore{
	private static final Logger logger = Logger.getLogger(SenseiServer.class);

    private final MBeanServer mbeanServer = java.lang.management.ManagementFactory.getPlatformMBeanServer();

    private final List<ObjectName> _registeredMBeans;
    private SenseiZoieFactory<?,?> _zoieFactory;
    private SenseiIndexLoaderFactory _indexLoaderFactory;
    private SenseiQueryBuilderFactory _queryBuilderFactory;
    private final HashSet<Zoie<BoboIndexReader,?,?>> zoieSystems = new HashSet<Zoie<BoboIndexReader,?,?>>();
    private final HashSet<SenseiIndexLoader> indexLoaders = new HashSet<SenseiIndexLoader>();
    private final int[] _partitions;
    private final int _id;
    private final Map<Integer,SenseiQueryBuilderFactory> _builderFactoryMap;
    private final Map<Integer,IndexReaderFactory<ZoieIndexReader<BoboIndexReader>>> _readerFactoryMap;
    private volatile boolean _started;
    
	public SenseiCore(int id,int[] partitions,
            File extDir,
            SenseiZoieFactory<?,?> zoieSystemFactory,
            SenseiIndexLoaderFactory indexLoaderFactory,
            SenseiQueryBuilderFactory queryBuilderFactory){

	      _registeredMBeans = new LinkedList<ObjectName>();
	      _zoieFactory = zoieSystemFactory;
	      _indexLoaderFactory = indexLoaderFactory;
	      _queryBuilderFactory = queryBuilderFactory;
	      _partitions = partitions;
	      _id = id;
	      
	      if (extDir!=null){
	        loadJars(extDir);
	      }
	      
	      _builderFactoryMap = new HashMap<Integer,SenseiQueryBuilderFactory>();
	      _readerFactoryMap = new HashMap<Integer,IndexReaderFactory<ZoieIndexReader<BoboIndexReader>>>();
	      _started = false;
	}
	
	private static void loadJars(File extDir)
	  {
	    File[] jarfiles = extDir.listFiles(new FilenameFilter(){
	        public boolean accept(File dir, String name) {
	            return name.endsWith(".jar");
	        }
	    });
	      
	    if (jarfiles!=null && jarfiles.length > 0){
	    try{
	        URL[] jarURLs = new URL[jarfiles.length];
	          ClassLoader parentLoader = Thread.currentThread().getContextClassLoader();
	          for (int i=0;i<jarfiles.length;++i){
	            jarURLs[i] = new URL("jar:file://" + jarfiles[i].getAbsolutePath() + "!/");  
	          }
	          URLClassLoader classloader = new URLClassLoader(jarURLs,parentLoader);
	          Thread.currentThread().setContextClassLoader(classloader);
	    }
	    catch(MalformedURLException e){
	      logger.error("problem loading extension: "+e.getMessage(),e);
	    }
	    }
	}
	
	public int[] getPartitions(){
		return _partitions;
	}
	
	public void start() throws Exception{
		if (_started) return;
	    for (int part : _partitions){
	      //in simple case query builder is the same for each partition
	      _builderFactoryMap.put(part, _queryBuilderFactory);
	      
	      Zoie<BoboIndexReader,?,?> zoieSystem = _zoieFactory.getZoieInstance(_id,part);
	      
	      // register ZoieSystemAdminMBean

	      String[] mbeannames = zoieSystem.getStandardMBeanNames();
	      for(String name : mbeannames)
	      {
	        ObjectName oname = new ObjectName("SenseiCore", "name", name + "-" + _id+"-"+part);
	        try
	        {
	          mbeanServer.registerMBean(zoieSystem.getStandardMBean(name), oname);
	          _registeredMBeans.add(oname);
	          logger.info("registered mbean " + oname);
	        } catch(Exception e)
	        {
	          logger.error(e.getMessage(),e);
	          if (e instanceof InstanceAlreadyExistsException)
	          {
	            _registeredMBeans.add(oname);
	          }
	        }        
	      }
	            
	      if(!zoieSystems.contains(zoieSystem))
	      {
	        zoieSystem.start();
	        zoieSystems.add(zoieSystem);
	      }
	      
	      SenseiIndexLoader loader = _indexLoaderFactory.getIndexLoader(part, zoieSystem);
	      if(!indexLoaders.contains(loader))
	      {
	        loader.start();
	        indexLoaders.add(loader);
	      }
	      _readerFactoryMap.put(part, zoieSystem);
	    }
	    _started = true;
	}
	
	public void shutdown(){
		if (!_started) return;
		logger.info("unregistering mbeans...");
	    try{
	      if (_registeredMBeans.size()>0){
	        for (ObjectName name : _registeredMBeans){
	          mbeanServer.unregisterMBean(name);
	        }
	        _registeredMBeans.clear();
	      }
	    }
	    catch(Exception e){
	      logger.error(e.getMessage(),e);
	    }
	   
        // shutdown the loaders
        for(SenseiIndexLoader loader : indexLoaders)
        {
          try{
            loader.shutdown();
          }
          catch(SenseiException se){
            logger.error(se.getMessage(),se);
          }
        }
        indexLoaders.clear();
        // shutdown the zoieSystems
        for(Zoie<BoboIndexReader,?,?> zoieSystem : zoieSystems)
        {
          zoieSystem.shutdown();
        }
        zoieSystems.clear();
        _started =false;
	}
	
	public IndexReaderFactory<ZoieIndexReader<BoboIndexReader>> getIndexReaderFactory(int partition){
		return _readerFactoryMap.get(partition);
	}
	
	public SenseiQueryBuilderFactory getQueryBuilderFactory(int partition){
		return _builderFactoryMap.get(partition);
	}
}
