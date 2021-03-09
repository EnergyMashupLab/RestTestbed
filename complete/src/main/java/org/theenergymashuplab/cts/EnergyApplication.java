/*
 * Copyright 2019-2020 The Energy Mashup Lab
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.theenergymashuplab.cts;

//	import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@SpringBootApplication
public class EnergyApplication {
	
	/*
	 * Global constants
	 */
	public final int LME_PORT = 39401;		// for Socket Server in LME takes CreateTransaction
	public final int MARKET_PORT = 39402;	// for Socket Server in Market takes CreateTender 

	public final int NUM_TEUAS;
	
	private static final Logger logger = LogManager.getLogger(
			EnergyApplication.class);
	// Application general logger from NIST-CTS-Agents
	
	private static RestTemplate restTemplate;
	final static RestTemplateBuilder builder = new RestTemplateBuilder();


    public static void main(String[] args) {
    	restTemplate = builder.build();
	
//    	new RandomCreateClientTender();	// on System.out before run
    	
        SpringApplication.run(EnergyApplication.class, args);
    }
    
	@Bean
	public RestTemplate restTemplate(RestTemplateBuilder builder) {
		return builder.build();
	}
	
	@Bean
	// Set up the restTemplate - only for debug print. May not be necessary
	// TODO EXPERIMENTAL - Candidate for deletion
	public CommandLineRunner run(RestTemplate restTemplate) throws Exception {
		return args -> {
			logger.info("In CommandLineRunner before getForObject");	
//			ActorId actorId = restTemplate.getForObject(
//					"https://lma/party", ActorId.class);
//			logger.info(actorId.toString());
			String numTeuas = args[1];
			NUM_TEUAS = Integer.parseInt(numTeuas);
			TeuaRestController.initMapArray(NUM_TEUAS);
	};

	/*
			System.err.println("before restTemplate in CommandLineRunner");
			ActorId actorId = restTemplate.getForObject(
					"http://localhost:8080/lma/party", ActorId.class);
			logger.info(actorId.toString());
	 */
}
	
    
	public static void WaitForAppReady() {
    	restTemplate = builder.build();
    	ActorIdType actorId;
 
		actorId = restTemplate.getForObject(
				"https://lma/party", ActorIdType.class);
		logger.info(actorId.toString());
		
		actorId = restTemplate.getForObject(
				"https://lme/party", ActorIdType.class);
		logger.info(actorId.toString());

		actorId = restTemplate.getForObject(
				"https://teua/party", ActorIdType.class);
		logger.info(actorId.toString());
	}
	
}
