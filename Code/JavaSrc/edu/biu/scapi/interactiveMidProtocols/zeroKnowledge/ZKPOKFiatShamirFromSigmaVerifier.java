/**
* %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
* 
* Copyright (c) 2012 - SCAPI (http://crypto.biu.ac.il/scapi)
* This file is part of the SCAPI project.
* DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
* 
* Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"),
* to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, 
* and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
* 
* The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
* 
* THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
* FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
* WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
* 
* We request that any publication and/or code referring to and/or based on SCAPI contain an appropriate citation to SCAPI, including a reference to
* http://crypto.biu.ac.il/SCAPI.
* 
* SCAPI uses Crypto++, Miracl, NTL and Bouncy Castle. Please see these projects for any further licensing issues.
* %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
* 
*/
package edu.biu.scapi.interactiveMidProtocols.zeroKnowledge;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;

import edu.biu.scapi.comm.Channel;
import edu.biu.scapi.interactiveMidProtocols.SigmaProtocol.SigmaVerifierComputation;
import edu.biu.scapi.interactiveMidProtocols.SigmaProtocol.utility.SigmaCommonInput;
import edu.biu.scapi.interactiveMidProtocols.SigmaProtocol.utility.SigmaProtocolMsg;
import edu.biu.scapi.primitives.randomOracle.HKDFBasedRO;
import edu.biu.scapi.primitives.randomOracle.RandomOracle;

/**
 * Concrete implementation of Zero Knowledge verifier.
 * 
 * This is a transformation that takes any Sigma protocol and a random oracle 
 * (instantiated with any hash function) H and yields a zero-knowledge proof of knowledge.
 * 
 * @author Cryptography and Computer Security Research Group Department of Computer Science Bar-Ilan University (Moriya Farbstein)
 *
 */
public class ZKPOKFiatShamirFromSigmaVerifier implements ZKPOKVerifier{

	private Channel channel;
	private SigmaVerifierComputation sVerifier; //Underlying verifier that computes the proof of the sigma protocol.
	private RandomOracle ro;					//Underlying random oracle to use.
	
	/**
	 * Constructor that accepts the underlying channel, sigma protocol's verifier and random oracle to use.
	 * @param channel
	 * @param sVerifier
	 * @param ro
	 */
	public ZKPOKFiatShamirFromSigmaVerifier(Channel channel, SigmaVerifierComputation sVerifier, RandomOracle ro) {
		
		this.sVerifier = sVerifier;
		this.ro = ro;
		this.channel = channel;
	}
	
	/**
	 * Constructor that accepts the underlying channel, sigma protocol's verifier and sets default random oracle.
	 * @param channel
	 * @param sVerifier
	 */
	public ZKPOKFiatShamirFromSigmaVerifier(Channel channel, SigmaVerifierComputation sVerifier){
	
		this.channel = channel;
		this.sVerifier = sVerifier;
		this.ro = new HKDFBasedRO();
	}
	
	/**
	 * Runs the verifier side of the Zero Knowledge proof.
	 * @param input must be an instance of ZKPOKFiatShamirInput that holds 
	 * 				input for the underlying sigma protocol and possible context information cont.
	 * @throws IOException if failed to send the message.
	 * @throws ClassNotFoundException 
	 */
	public boolean verify(ZeroKnowledgeCommonInput input) throws ClassNotFoundException, IOException{
		
		//Wait for a message a from P
		ZKPOKFiatShamirProof msg = receiveMsgFromProver();
		
		//verify the proof.
		return verifyFiatShamirProof(input, msg);
	}
	
	/**
	 *  Let (a,e,z) denote the prover1, verifier challenge and prover2 messages of the sigma protocol.
	 * This function computes the following calculations:
	 *
	 *		IF
	 *			�	e=H(x,a,cont), AND
	 *			�	Transcript (a, e, z) is accepting in sigma on input x
	 *     		OUTPUT ACC
	 *     ELSE
	 *          OUTPUT REJ
	 * @param input must be an instance of ZKPOKFiatShamirInput that holds 
	 * 				input for the underlying sigma protocol and possible context information cont.
	 * @param input
	 * @param msg
	 * @return true if the proof is valid; false, otherwise.
	 * @throws IOException 
	 * @throws IllegalArgumentException if the given input is not an instance of ZKPOKFiatShamirInput.
	 */
	public boolean verifyFiatShamirProof(ZeroKnowledgeCommonInput input, ZKPOKFiatShamirProof msg) throws IOException{
		//The given input must be an instance of ZKPOKFiatShamirInput that holds input for the underlying sigma protocol and possible context information cont.
		if (!(input instanceof ZKPOKFiatShamirCommonInput)){
			throw new IllegalArgumentException("the given input must be an instance of ZKPOKFiatShamirInput");
		}
		
		//get the given a
		SigmaProtocolMsg a = msg.getA();
		
		//Compute e=H(x,a,cont)
		byte[] computedE = computeChallenge((ZKPOKFiatShamirCommonInput) input, a);
		
		//get the given challenge.
		byte[] receivedE = msg.getE();
		
		//check that e=H(x,a,cont):
		boolean valid = true;
		//In case that lengths of computed e and received e are not the same, set valid to false.
		if (computedE.length != receivedE.length){
			valid = false;
		}
		
		//In case that  computed e and received e are not the same, set valid to false.
		for (int i = 0; i<computedE.length; i++){
			if (computedE[i] != receivedE[i]){
				valid = false;
			}
		}
			
		//get the received z
		SigmaProtocolMsg z = msg.getZ();
		
		//If transcript (a, e, z) is accepting in sigma on input x, output ACC
		//Else outupt REJ
		valid = valid && proccessVerify(((ZKPOKFiatShamirCommonInput) input).getSigmaInput(), a, computedE, z);
		
		return valid;
	}
	
	/**
	 * Waits for a message a from the prover.
	 * @return the received message
	 * @throws ClassNotFoundException
	 * @throws IOException if failed to send the message.
	 */
	private ZKPOKFiatShamirProof receiveMsgFromProver() throws ClassNotFoundException, IOException {
		Serializable msg = null;
		try {
			//receive the mesage.
			msg = channel.receive();
		} catch (IOException e) {
			throw new IOException("failed to receive the a message. The thrown message is: " + e.getMessage());
		}
		//If the given message is not an instance of SigmaProtocolMsg, throw exception.
		if (!(msg instanceof ZKPOKFiatShamirProof)){
			throw new IllegalArgumentException("the given message should be an instance of ZKPOKFiatShamirMessage");
		}
		//Return the given message.
		return (ZKPOKFiatShamirProof) msg;
	}
	
	/**
	 * Run the following line from the protocol:
	 * "COMPUTE e=H(x,a,cont)".
	 * @param input 
	 * @param a first message of the sigma protocol.
	 * @return the computed challenge
	 * @throws IOException 
	 */
	private byte[] computeChallenge(ZKPOKFiatShamirCommonInput input, SigmaProtocolMsg a) throws IOException {
		byte[] inputArray = convertToBytes(input.getSigmaInput());
		byte[] messageArray = convertToBytes(a);
		byte[] cont = input.getContext();
		
		byte[] inputToRO = null;
		
		if (cont != null){
			inputToRO = new byte[inputArray.length + messageArray.length + cont.length];
			System.arraycopy(cont, 0, inputToRO, inputArray.length + messageArray.length, cont.length);
		} else{
			inputToRO = new byte[inputArray.length + messageArray.length];
		}
		System.arraycopy(inputArray, 0, inputToRO, 0, inputArray.length);
		System.arraycopy(messageArray, 0, inputToRO, inputArray.length, messageArray.length);
		
		return ro.compute(inputToRO, 0, inputToRO.length, sVerifier.getSoundnessParam()/8);
	}
	
	/**
	 * Converts the given data to byte array using serialization mechanism.
	 * @param data to convert.
	 * @return the converted bytes.
	 * @throws IOException
	 */
	private byte[] convertToBytes(Serializable data) throws IOException{
		ByteArrayOutputStream bOut = new ByteArrayOutputStream();  
	    ObjectOutputStream oOut  = new ObjectOutputStream(bOut);
		oOut.writeObject(data);  
		oOut.close();
		
		return bOut.toByteArray();
	}
	
	/**
	 * Verifies the proof.
	 * @param input2 
	 * @param a first message from prover.
	 * @throws IOException if failed to send the message.
	 * @throws ClassNotFoundException 
	 */
	private boolean proccessVerify(SigmaCommonInput input, SigmaProtocolMsg a, byte[] challenge, SigmaProtocolMsg z) {
		//If transcript (a, e, z) is accepting in sigma on input x, output ACC
		//Else outupt REJ
		
		sVerifier.setChallenge(challenge);
		
		return sVerifier.verify(input, a, z);
	}
}

