package com.sensei.search.query.filters;

import java.util.Iterator;
import java.util.HashMap;
import java.util.Map;

import org.apache.lucene.queryParser.QueryParser;
import org.apache.lucene.search.Filter;
import org.json.JSONObject;

public abstract class FilterConstructor {

  public static final String VALUES_PARAM     = "values";
  public static final String EXCLUDES_PARAM   = "excludes";
  public static final String OPERATOR_PARAM   = "operator";
  public static final String PARAMS_PARAM     = "params";
  public static final String MUST_PARAM       = "must";
  public static final String MUST_NOT_PARAM   = "must_not";
  public static final String SHOULD_PARAM     = "should";
  public static final String FROM_PARAM       = "from";
  public static final String TO_PARAM         = "to";
  public static final String NOOPTIMIZE_PARAM = "_noOptimize";
  public static final String QUERY_PARAM      = "query";
  public static final String OR_PARAM         = "or";
	
  private static final Map<String, FilterConstructor> FILTER_CONSTRUCTOR_MAP = 
    new HashMap<String, FilterConstructor>();

  static
  {
    FILTER_CONSTRUCTOR_MAP.put(UIDFilterConstructor.FILTER_TYPE, new UIDFilterConstructor());
    FILTER_CONSTRUCTOR_MAP.put(FacetSelectionFilterConstructor.FILTER_TYPE, new FacetSelectionFilterConstructor());
    FILTER_CONSTRUCTOR_MAP.put(RangeFilterConstructor.FILTER_TYPE, new RangeFilterConstructor());
    FILTER_CONSTRUCTOR_MAP.put(TermFilterConstructor.FILTER_TYPE, new TermFilterConstructor());
    FILTER_CONSTRUCTOR_MAP.put(PathFilterConstructor.FILTER_TYPE, new PathFilterConstructor());
    FILTER_CONSTRUCTOR_MAP.put(TermsFilterConstructor.FILTER_TYPE, new TermsFilterConstructor());
  }
  
  public static FilterConstructor getFilterConstructor(String type, QueryParser qparser)
  {
    FilterConstructor filterConstructor = FILTER_CONSTRUCTOR_MAP.get(type);
    if (filterConstructor == null)
    {
      if (QueryFilterConstructor.FILTER_TYPE.equals(type))
        filterConstructor = new QueryFilterConstructor(qparser);
      else if (AndFilterConstructor.FILTER_TYPE.equals(type))
        filterConstructor = new AndFilterConstructor(qparser);
      else if (OrFilterConstructor.FILTER_TYPE.equals(type))
        filterConstructor = new OrFilterConstructor(qparser);
      else if (BooleanFilterConstructor.FILTER_TYPE.equals(type))
        filterConstructor = new BooleanFilterConstructor(qparser);
    }
    return filterConstructor;
  }
	
	public static Map<String,String> convertParams(JSONObject obj){
		Map<String,String> paramMap = new HashMap<String,String>();
		String[] names = JSONObject.getNames(obj);
		if (names!=null){
		  for (String name:names){
			String val = obj.optString(name, null);
			if (val!=null){
				paramMap.put(name, val);
			}
		  }
		}
		return paramMap;
	}

	public static Filter constructFilter(JSONObject json, QueryParser qparser) throws Exception
  {
    if (json == null)
      return null;

    Iterator<String> iter = json.keys();
    if (!iter.hasNext())
      throw new IllegalArgumentException("Filter type not specified: " + json);

    String type = iter.next();

    FilterConstructor filterConstructor = FilterConstructor.getFilterConstructor(type, qparser);
    if (filterConstructor == null)
      throw new IllegalArgumentException("Filter type '" + type + "' not supported");

    return filterConstructor.doConstructFilter(json.getJSONObject(type));
  }
	
	abstract protected Filter doConstructFilter(Object json/* JSONObject or JSONArray */) throws Exception;

}
