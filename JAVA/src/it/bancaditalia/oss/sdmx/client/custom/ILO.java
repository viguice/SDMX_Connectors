/* Copyright 2010,2014 Bank Of Italy
*
* Licensed under the EUPL, Version 1.1 or - as soon they
* will be approved by the European Commission - subsequent
* versions of the EUPL (the "Licence");
* You may not use this work except in compliance with the
* Licence.
* You may obtain a copy of the Licence at:
*
*
* http://ec.europa.eu/idabc/eupl
*
* Unless required by applicable law or agreed to in
* writing, software distributed under the Licence is
* distributed on an "AS IS" basis,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
* express or implied.
* See the Licence for the specific language governing
* permissions and limitations under the Licence.
*/
package it.bancaditalia.oss.sdmx.client.custom;

import it.bancaditalia.oss.sdmx.api.DataFlowStructure;
import it.bancaditalia.oss.sdmx.api.Dataflow;
import it.bancaditalia.oss.sdmx.parser.v20.DataStructureParser;
import it.bancaditalia.oss.sdmx.parser.v21.RestQueryBuilder;
import it.bancaditalia.oss.sdmx.util.Configuration;
import it.bancaditalia.oss.sdmx.util.SdmxException;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Attilio Mattiocco
 *
 */
public class ILO extends RestSdmx20Client {
		
	protected static Logger logger = Configuration.getSdmxLogger();
	
	public ILO() throws MalformedURLException{
		super("ILO", new URL("http://www.ilo.org/ilostat/sdmx/ws/rest"), false);
	}


//	@Override
//	protected String buildFlowQuery(URL endpoint, String flow, String agency, String version) throws SdmxException{
//		agency = (agency == null) ? "ALL" : agency;
//		version = (version == null) ? "latest" : version;
//		if( endpoint!=null  &&
//				flow!=null && !flow.isEmpty()){
//	
//			String query = endpoint + "/dataflow/ILO/" + flow;
//			return query;
//		}
//		else{
//			throw new RuntimeException("Invalid query parameters: dataflow=" + flow + " endpoint=" + endpoint);
//		}
//	}

	@Override
	public Map<String, Dataflow> getDataflows() throws SdmxException {
		Map<String, Dataflow> result = new HashMap<String, Dataflow>();
		Map<String, String> collections = getCodes("CL_COLLECTION", "ILO", "latest");
		// this algorithm will be replaced as soon as the ILO provider supports 
		// 'ALL_MULTI' queries for dataflows
		for (Iterator<String> iterator = collections.keySet().iterator(); iterator.hasNext();) {
			String coll = (String) iterator.next();
			String query = wsEndpoint + "/datastructure" + "/ILO/" + coll + "_ALL_MULTI";
			InputStreamReader xmlStream = null;
			xmlStream = runQuery(query, null);				
			if(xmlStream!=null){
				try {
					List<DataFlowStructure> dfs = DataStructureParser.parse(xmlStream);
					if(dfs.size() > 0){
						for (Iterator<DataFlowStructure> iterator1 = dfs.iterator(); iterator1.hasNext();) {
							DataFlowStructure dsd = (DataFlowStructure) iterator1.next();
							Dataflow tmp = new Dataflow();
							tmp.setId("DF_" + dsd.getId());
							tmp.setName(dsd.getName());
							tmp.setAgency(dsd.getAgency());
							tmp.setVersion(dsd.getVersion());					
							result.put(tmp.getId(), tmp);
						}
					}
				} catch (Exception e) {
					logger.severe("Exception caught parsing results from call to provider " + name);
					logger.log(Level.FINER, "Exception: ", e);
					throw new SdmxException("Exception. Class: " + e.getClass().getName() + " .Message: " + e.getMessage());
				} finally{
					try {
						xmlStream.close();
					} catch (IOException e) {
						logger.severe("Exception caught closing stream.");
					}
				}
			}
			else{
				throw new SdmxException("The query returned anull stream");
			}
		}
		return result;
	}

	@Override
	protected String buildDSDQuery(URL endpoint, String dsd, String agency, String version){
		agency = "ILO";
		if( endpoint!=null  && dsd!=null && !dsd.isEmpty()){

			String query = endpoint + "/datastructure/" + agency + "/" + dsd;
			return query;
		}
		else{
			throw new RuntimeException("Invalid query parameters: dsd=" + dsd + " endpoint=" + endpoint);
		}
	}

	@Override
	protected String buildDataQuery(URL endpoint, Dataflow dataflow, String resource, String startTime, String endTime){
		if( endpoint!=null && 
				dataflow!=null &&
				resource!=null && !resource.isEmpty()){

			// for ILO use the simple DF id
			String query = endpoint + "/data/" + dataflow.getFullIdentifier() + "/";
			query += resource ;
			
			//query=query+"?";
			//query += "&format=compact_v2";
			query += RestQueryBuilder.addTime(startTime, endTime);
			return query;
		}
		else{
			throw new RuntimeException("Invalid query parameters: dataflow=" + dataflow + " resource=" + resource + " endpoint=" + endpoint);
		}
	}
}