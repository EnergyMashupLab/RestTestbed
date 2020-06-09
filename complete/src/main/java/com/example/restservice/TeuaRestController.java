package com.example.restservice;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

//For RestTemplate
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;

/*
 * NEXT STEPS: incorporate dynamic URIs for the TEUAs, of the form
 *  /teua/{number}/party, etc.
 */
@RestController
@RequestMapping("/teua")	// use dynamic URIs - this supports one
public class TeuaRestController {
	private static final AtomicLong counter = new AtomicLong();
	private static EiTender currentTender;
	private static EiTransaction currentTransaction;
	private static TenderIdType currentTenderId;
	// TODO assign in constructor?
	private final ActorIdType partyId  = new ActorIdType();
	private ActorIdType marketPartyId;
	private ActorIdType lmePartyId = null;

	private static final Logger logger = LogManager.getLogger(
			TeuaRestController.class);
	

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
	 */
	
	@PostMapping("/createTransaction")
	public EiCreatedTransactionPayload postEiCreateTransactionPayload(
			@RequestBody EiCreateTransactionPayload eiCreateTransactionPayload)	{
		EiTender tempTender;
		EiTransaction tempTransaction;
		// tempPostReponse responds to POST to /sc
		EiCreateTransactionPayload tempCreate;
		EiCreatedTransactionPayload tempCreated, tempPostResponse;
		ClientCreatedTransactionPayload clientCreated;
		ClientCreateTransactionPayload	clientCreate;
		
		// Is class scope OK for builder?
		final RestTemplateBuilder builder = new RestTemplateBuilder();
		// scope is function postEiCreateTransactionPayload
		RestTemplate restTemplate;	
	   	restTemplate = builder.build();
		
		tempCreate = eiCreateTransactionPayload;
		tempTransaction = eiCreateTransactionPayload.getTransaction();
		tempTender = tempCreate.getTransaction().getTender();
//		logger.debug("TEUA received createTransaction " +
//				tempCreate.toString() + " before forward to Client " 
//				);
		

		/*
		 * Build ClientCreateTransactionPayload to POST to client
		 */
//		logger.info("Build ClientCreateTransactionPayload");
		
		clientCreate = new ClientCreateTransactionPayload(
				/* side 	*/	tempTender.getSide(),
				/* quantity	*/	tempTender.getQuantity(),
				/* price	*/	tempTender.getPrice(),
				/* tenderId	*/	tempTender.getTenderId().value()
				);
		
//		logger.info("Built ClientCreateTransactionPayload " +
//				clientCreate.toString());
		// and post
		clientCreated = restTemplate.postForObject(
					"http://localhost:8080/client/clientCreateTransaction", 
					clientCreate,
					ClientCreatedTransactionPayload.class);
		
//		System.err.println("TEUA POSTed to Client/SC ");
//		logger.debug("POSTED TO CLIENT/SC ");
		
		//	Log return value (success true or false)
//		logger.debug("TEUA return from client/SC " + clientCreated.getSuccess());
		
		// 	Return EiCreatedTransactionPayload to sender
		//	NOTE responds before response from Client/SC
		//	TODO change EiResponse code if clientCreated indicates failure
		tempCreated = new EiCreatedTransactionPayload(
				tempTransaction.getTransactionId(),
				tempCreate.getPartyId(),
				tempCreate.getCounterPartyId(),
				new EiResponse(200, "OK"));
		
//		logger.info("tempCreated constructed before return " + tempCreated.toString());
		
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
		TenderIdType tempTenderId;
		EiCancelTenderPayload tempCancel;	
		EICanceledTenderPayload tempCanceled;
		
		tempCancel = eiCancelTender;
		tempTenderId = eiCancelTender.getTenderId();

		
		
		tempCanceled = new EICanceledTenderPayload(
				tempCancel.getPartyId(),
				tempCancel.getCounterPartyId(),
				new EiResponse(200, "OK"));
		
		return tempCanceled;
	}

	
	/*kl
	 * POST - /clientCreateTender processed and sent to LMA,
	 * received from Client/SC
	 * 		RequestBody is ClientCreateTenderPayload
	 * 		ResponseBody is ClientCreatedTenderPayload
	 * 
	 * Forward EiCreateTenderPayload to LMA
	 */
	
	@PostMapping("/clientCreateTender")
	public ClientCreatedTenderPayload postEiCreateTender(
				@RequestBody ClientCreateTenderPayload clientCreateTender)	{
		TenderIdType tempTenderId;
		ClientCreateTenderPayload tempCreate;	
		ClientCreatedTenderPayload tempCreated, tempReturn;
		EiTender tender;
		EiCreateTenderPayload eiCreateTender;
		EiCreatedTenderPayload lmaCreatedTender;
		
		
		final RestTemplateBuilder builder = new RestTemplateBuilder();
		// scope is function postEiCreateTender
		RestTemplate restTemplate = builder.build();
		
		//RestTemplate restTemplate = builder.build();
		
		if (lmePartyId == null)	{
			// builder = new RestTemplateBuilder();
			restTemplate = builder.build();
			lmePartyId = restTemplate.getForObject(
					"http://localhost:8080/lme/party",
					ActorIdType.class);
//			System.err.println("clientCreateTender: lmePartyId = " +
//				lmePartyId.toString());
		}
			
		// TODO address JSON serialization issues as needed	
		tempCreate = clientCreateTender;	// save the parameter

		/*
		 * Create a new EiTender using the interval, quantity, price,and expiration
		 * time  sent by the Client/SC, and insert it via the constructor in a
		 * new EiCreateTenderPayload.
		 * 
		 * partyId is this.partyId, counterPartyId is the LME representing
		 * the market and the POST is to the LMA..
		 */
		tender = new EiTender(
				tempCreate.getInterval(), tempCreate.getQuantity(),
				tempCreate.getPrice(), tempCreate.getBridgeExpireTime().asInstant(),
				tempCreate.getSide());
		
		// 	Construct the EiCreateTender payload to be forwarded to LMA
		eiCreateTender = new EiCreateTenderPayload(tender, this.partyId,
				this.lmePartyId);
//			System.err.println("clientCreateTender: eiCreateTender is " +
//					eiCreateTender.toString());	
		
		logger.info("TEUA sending to LMA " +
				eiCreateTender.getTender().toString());
		
		
		//	And forward to the LMA
		restTemplate = builder.build();
		EiCreatedTenderPayload result = restTemplate.postForObject
			("http://localhost:8080/lma/createTender", eiCreateTender,
					EiCreatedTenderPayload.class);
		
		// and select CTS TenderId and put in ClientCreatedTenderPayload
		
		tempReturn = new ClientCreatedTenderPayload(result.getTenderId().value());
//		logger.debug("TEUA before return ClientCreatedTender to Client/SC " +
//				tempReturn.toString());
		
		return tempReturn;
	}
	
	
}
