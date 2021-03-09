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

package org.theenergymashuplab.cts.controller;

//import java.util.Random;
//import java.util.concurrent.atomic.AtomicLong;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.theenergymashuplab.cts.ActorIdType;
import org.theenergymashuplab.cts.EiResponse;
import org.theenergymashuplab.cts.EiTender;
import org.theenergymashuplab.cts.EiTransaction;
import org.theenergymashuplab.cts.TenderIdType;
import org.theenergymashuplab.cts.controller.payloads.ClientCreateTenderPayload;
import org.theenergymashuplab.cts.controller.payloads.ClientCreateTransactionPayload;
import org.theenergymashuplab.cts.controller.payloads.ClientCreatedTenderPayload;
import org.theenergymashuplab.cts.controller.payloads.ClientCreatedTransactionPayload;
import org.theenergymashuplab.cts.controller.payloads.EICanceledTenderPayload;
import org.theenergymashuplab.cts.controller.payloads.EiCancelTenderPayload;
import org.theenergymashuplab.cts.controller.payloads.EiCreateTenderPayload;
import org.theenergymashuplab.cts.controller.payloads.EiCreateTransactionPayload;
import org.theenergymashuplab.cts.controller.payloads.EiCreatedTenderPayload;
import org.theenergymashuplab.cts.controller.payloads.EiCreatedTransactionPayload;
import org.theenergymashuplab.cts.dto.EiCanceledResponse;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

//For RestTemplate
@RestController
@RequestMapping("/teua")	// use dynamic URIs - this supports one
public class TeuaRestController {

	private final ActorIdType partyId  = new ActorIdType();
	private ActorIdType lmePartyId = null;

	private static final Logger logger = LogManager.getLogger(
			TeuaRestController.class);
	
    /*
	 *	Two arrays use teua/{id}/... and matching client/{id} to give the
	 *	Actor ID and the URI string to which to post.postLmaToTeuaByPartyId
	 *	will be copied to the LMA so the LMA knows where to post an 
	 *	EiCreateTransactionPayload directly
	 *	TODO verify on use that the {id} string is convertable to int
	 */
	private Long[] actorNumericIds; 	    // actorNumericIds for each client/{id}
	private ActorIdType[] actorIds;			// ActorIdType values for the created actors
	String[] postClientCreateTransactionUri;	// URI to post to client[i]

//	public static ConcurrentHashMap<ActorIdType, String> postLmaToTeuaPartyIdMap; in LMA
    
    // for managing client/{id} and teua/{id}
    public final int DEFAULT_COUNT = 20;
    public final int MAX_COUNT = 2000;
    private int idLimit;
	static final String LMA_IP_ADDRESS = "172.16.1.10";
	static final String TEUA_IP_ADDRESS = "172.16.1.11";
	
	// Constructor for class TeuaRestController - zero parameters
	public TeuaRestController()	{
		this.idLimit = DEFAULT_COUNT;
		logger.trace("Built TeuaRestController zero param constructor idLimit " +
					this.idLimit);
		initMapArray(idLimit);
	}

	// Constructor for class ClientRestController - zero parameters
	public TeuaRestController(int howMany)	{
		if (howMany > MAX_COUNT)	{
			logger.trace("Constructor one parameter howMany " + howMany +
					" > " + MAX_COUNT + " set to " + DEFAULT_COUNT);
			this.idLimit = DEFAULT_COUNT;
		}	else {
			idLimit = howMany;
		}	
		initMapArray(idLimit);
	}
	
	// called by both constructors
	public static void initMapArray(int howMany)	{
		int i;    
		String clientUri, teuaUri;

	    //	HOOK for SC/Client IP address/port - default is localhost
	    String clientUriPrefix = "http://" + TEUA_IP_ADDRESS + ":8080/client/";
	    String clientUriSuffix = "/clientCreateTransaction";

	    String teuaUriPrefix = "http://" + TEUA_IP_ADDRESS + ":8080/teua/";
	    String teuaUriSuffix = "/createTransaction";
	    ActorIdType tempActorId;
	    String mapReturns;
	
		// Initialize global parallel arrays
	    actorNumericIds = new Long[idLimit];
	    actorIds = new ActorIdType[idLimit];
		postClientCreateTransactionUri = new String[this.idLimit];
		//	Triple the size should limit collisions and chain length
		LmaRestController.postLmaToTeuaPartyIdMap = 
					new ConcurrentHashMap<Long, String>(idLimit*3);

		for (i = 0; i < this.idLimit; i++)	{
			clientUri = clientUriPrefix + String.valueOf(i) + clientUriSuffix;
			postClientCreateTransactionUri[i] = clientUri;
			tempActorId = new ActorIdType();
			actorIds[i] = tempActorId;
			actorNumericIds[i] = tempActorId.value();
			teuaUri = teuaUriPrefix + String.valueOf(i) + teuaUriSuffix;
			mapReturns = LmaRestController.postLmaToTeuaPartyIdMap.put(actorNumericIds[i], teuaUri);
			
			logger.trace("mapReturns '"+ mapReturns + "' for key " + actorNumericIds[i]);
			logger.trace("Map size " + LmaRestController.postLmaToTeuaPartyIdMap.size() +
					" i = " + i + " " + clientUri + " " + teuaUri +
					" actorId " + actorNumericIds[i].toString());
		}
	}
	
	/*
	 * GET - /teua/{#}/party responds with PartyId json
	 */
	@GetMapping("/party")
	public ActorIdType getParty() {
		return this.partyId;
	}
	
	
	/*
	 * POST - /createTransaction
	 * 		RequestBody is EiCreateTransaction
	 * 		ResponseBody is EiCreatedTransaction
	 * 
	 * Position for this Party was updated in LMA
	 */
	
	@PostMapping("{teuaId}/createTransaction")
	public EiCreatedTransactionPayload postEiCreateTransactionPayload(
			@PathVariable String teuaId,
			@RequestBody EiCreateTransactionPayload eiCreateTransactionPayload
			)	{
		EiTender tempTender;
		EiTransaction tempTransaction;
		// tempPostReponse responds to POST to /sc
		EiCreateTransactionPayload tempCreate;
		EiCreatedTransactionPayload tempCreated;
		ClientCreatedTransactionPayload clientCreated;
		ClientCreateTransactionPayload	clientCreate;
		Integer numericTeuaId = -1;
		
		// Is class scope OK for builder?
		final RestTemplateBuilder builder = new RestTemplateBuilder();
		// scope is function postEiCreateTransactionPayload
		RestTemplate restTemplate;	
	   	restTemplate = builder.build();
		
		tempCreate = eiCreateTransactionPayload;
		tempTransaction = eiCreateTransactionPayload.getTransaction();
		tempTender = tempCreate.getTransaction().getTender();
		logger.trace("TEUA received createTransaction " +
				tempCreate.toString() + " before forward to Client " 
				);
		
		/*
		 * Build ClientCreateTransactionPayload to POST to client with same id
		 */	
		clientCreate = new ClientCreateTransactionPayload(
				/* side 	*/	tempTender.getSide(),
				/* quantity	*/	tempTender.getQuantity(),
				/* price	*/	tempTender.getPrice(),
				/* tenderId	*/	tempTender.getTenderId().value()
				);
		
		logger.trace("Built ClientCreateTransactionPayload " +
				clientCreate.toString());

		numericTeuaId = Integer.valueOf(teuaId);
		logger.debug(" Forwarding ClientCreateTransaction to " +
				postClientCreateTransactionUri[numericTeuaId]);
		
		clientCreated = restTemplate.postForObject(
				postClientCreateTransactionUri[numericTeuaId],
					clientCreate,
					ClientCreatedTransactionPayload.class);
		
		//	Log return value (success true or false)
		logger.debug("TEUA return from client/SC " + clientCreated.getSuccess());

		// 	Return EiCreatedTransactionPayload to sender
		//	NOTE responds before response from Client/SC
		//	TODO change EiResponse code if clientCreated indicates failure
		tempCreated = new EiCreatedTransactionPayload(
				tempTransaction.getTransactionId(),
				tempCreate.getPartyId(),
				tempCreate.getCounterPartyId(),
				new EiResponse(200, "OK"));
		
		logger.debug("tempCreated constructed before return " + tempCreated.toString());
		
		return tempCreated;
	}
	
	
	/*
	 * POST - /cancelTender sent to LMA, received from Client/SC
	 * 		RequestBody is EiCancelTender
	 * 		ResponseBody is EiCanceledTender
	 */
	
	@PostMapping("/cancelTender")
	public EICanceledTenderPayload postEiCancelTender(
				@RequestBody EiCancelTenderPayload eiCancelTender)	{
		final RestTemplate restTemplate = new RestTemplate();

		return restTemplate.postForObject("http://localhost:8080/lma/cancelTender", eiCancelTender,
				EICanceledTenderPayload.class);
	}

	
	/*
	 * POST - /clientCreateTender processed and sent to LMA,
	 * received from Client/SC
	 * 		RequestBody is ClientCreateTenderPayload
	 * 		ResponseBody is ClientCreatedTenderPayload
	 * 
	 * Query PositionManager for the TEUA's PartyId, net full requirements energy request
	 * (positive or negative) and forward the EiCreateTenderPayload with adjusted quanity
	 * and possible different Side to LMA
	 * 
	 * Processing the request from the Building Controller (SC/Client)
	 * Return the ClientCreatedTenderPayload which is just CTS TenderId for new tender
	 * 
	 * NOTE that the quantity in a ClientCreateTender is FULL REQUIREMENTS for the
	 * Interval. The User Agent will adjust that request by energy already bought or sold
	 * on behalf of this client for the Interval, to get a net amount to go from the client's position
	 * (energy already bought or sold, netted) to the Full Requirements amount for Interval.
	 */
	@PostMapping("{teuaId}/clientCreateTender")
	public ClientCreatedTenderPayload postClientCreateTender(
			@PathVariable String teuaId,
			@RequestBody ClientCreateTenderPayload clientCreateTender)	{
		ClientCreateTenderPayload tempClientCreateTender;	
		ClientCreatedTenderPayload tempReturn;
		EiTender tender;
		EiCreateTenderPayload eiCreateTender;	
		Integer numericTeuaId = -1;
		String positionUri;
		
		
		final RestTemplateBuilder builder = new RestTemplateBuilder();
		// scope is function postEiCreateTender
		RestTemplate restTemplate = builder.build();
				
		if (lmePartyId == null)	{
			// builder = new RestTemplateBuilder();
			restTemplate = builder.build();
			lmePartyId = restTemplate.getForObject(
					"http://localhost:8080/lme/party",
					ActorIdType.class);
		}
		
		numericTeuaId = Integer.valueOf(teuaId);
		
		
		//convert to URI for position manager
		positionUri = "/position/" 
				 + actorIds[numericTeuaId] +
				"/getPosition";
		logger.debug("positionUri is " + positionUri);
		
		logger.debug("numericTeuaId is " + numericTeuaId +" String is " + teuaId);		
		logger.debug("postEiCreateTender teuaId " +
			teuaId +
			" actorNumericIds[teuaId] " +
			actorIds[numericTeuaId].toString());
		
		tempClientCreateTender = clientCreateTender;	// save the parameter

		/*
		 * Create a new EiTender using the interval, quantity, price,and expiration
		 * time  sent by the Client/SC, and insert it via the constructor in a
		 * new EiCreateTenderPayload.
		 * 
		 * partyId is in actorNumericIds[] , counterPartyId is the LME representing
		 * the market and the POST is to the LMA.
		 * 
		 * if Building sends to /teua/7 that means it's client 7
		 */
		tender = new EiTender(
				tempClientCreateTender.getInterval(),
				tempClientCreateTender.getQuantity(),
				tempClientCreateTender.getPrice(),
				tempClientCreateTender.getBridgeExpireTime().asInstant(),
				tempClientCreateTender.getSide());
		
		// 	Construct the EiCreateTender payload to be forwarded to LMA
		eiCreateTender = new EiCreateTenderPayload(tender, actorIds[numericTeuaId],
				this.lmePartyId);
		// set party and counterParty -partyId saved in actorIds, counterParty is lmePartyId
		eiCreateTender.setPartyId(actorIds[numericTeuaId]);
		eiCreateTender.setCounterPartyId(lmePartyId);
		
		logger.trace("TEUA sending EiCreateTender to LMA " +
				eiCreateTender.toString());
			
		//	And forward to the LMA
		restTemplate = builder.build();
		EiCreatedTenderPayload result = restTemplate.postForObject
			("http://localhost:8080/lma/createTender", eiCreateTender,
					EiCreatedTenderPayload.class);
		
		// and put CtsTenderId in ClientCreatedTenderPayload
		tempReturn = new ClientCreatedTenderPayload(result.getTenderId().value());
		logger.trace("TEUA before return ClientCreatedTender to Client/SC " +
				tempReturn.toString());
		
		return tempReturn;
	}
	
}
