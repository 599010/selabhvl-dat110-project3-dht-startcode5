/**
 * 
 */
package no.hvl.dat110.chordoperations;

import java.math.BigInteger;
import java.rmi.RemoteException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import no.hvl.dat110.middleware.Message;
import no.hvl.dat110.middleware.Node;
import no.hvl.dat110.rpc.interfaces.NodeInterface;
import no.hvl.dat110.util.Hash;
import no.hvl.dat110.util.Util;

/**
 * @author tdoy
 *
 */
public class ChordLookup {

	private static final Logger logger = LogManager.getLogger(ChordLookup.class);
	private Node node;
	
	public ChordLookup(Node node) {
		this.node = node;
	}
	
	public NodeInterface findSuccessor(BigInteger key) throws RemoteException {
	    NodeInterface currentNode = this.node;
	    BigInteger currentNodeId = currentNode.getNodeID();
	    
	    // First check if the key is within the current node and its successor
	    if (Util.checkInterval(key, currentNodeId.add(BigInteger.ONE), currentNode.getSuccessor().getNodeID())) {
	        return currentNode.getSuccessor();
	    }

	    // Otherwise, start the iterative process
	    while (true) {
	        // Find the highest predecessor of the key in the current node's finger table
	        NodeInterface highestPredecessor = findHighestPredecessor(currentNode, key);
	        BigInteger highestPredecessorId = highestPredecessor.getNodeID();

	        // If the highest predecessor is the current node itself, use its successor
	        if (highestPredecessorId.equals(currentNodeId)) {
	            return highestPredecessor.getSuccessor();
	        }
	        
	        // Move the current node to the highest predecessor
	        currentNode = highestPredecessor;
	        currentNodeId = currentNode.getNodeID();

	        // Check if the key is within the new current node and its successor
	        BigInteger successorId = currentNode.getSuccessor().getNodeID();
	        if (Util.checkInterval(key, currentNodeId.add(BigInteger.ONE), successorId)) {
	            return currentNode.getSuccessor();
	        }

	        
	    }
	}

	private NodeInterface findHighestPredecessor(NodeInterface currentNode, BigInteger key) throws RemoteException {
	    List<NodeInterface> fingerTable = currentNode.getFingerTable();
	    BigInteger currentNodeId = currentNode.getNodeID();

	    // Iterate over the finger table from farthest to closest
	    for (int i = fingerTable.size() - 1; i >= 0; i--) {
	        NodeInterface finger = fingerTable.get(i);
	        BigInteger fingerId = finger.getNodeID();

	        // Find the closest preceding node of the key
	        if (Util.checkInterval(fingerId, currentNodeId.add(BigInteger.ONE), key.subtract(BigInteger.ONE))) {
	            return finger;
	        }
	    }

	    // Return current node if no closer node is found in the finger table
	    return currentNode;
	}




	public void copyKeysFromSuccessor(NodeInterface succ) {
		
		Set<BigInteger> filekeys;
		try {
			// if this node and succ are the same, don't do anything
			if(succ.getNodeName().equals(node.getNodeName()))
				return;
			
			logger.info("copy file keys that are <= "+node.getNodeName()+" from successor "+ succ.getNodeName()+" to "+node.getNodeName());
			
			filekeys = new HashSet<>(succ.getNodeKeys());
			BigInteger nodeID = node.getNodeID();
			
			for(BigInteger fileID : filekeys) {

				if(fileID.compareTo(nodeID) <= 0) {
					logger.info("fileID="+fileID+" | nodeID= "+nodeID);
					node.addKey(fileID); 															// re-assign file to this successor node
					Message msg = succ.getFilesMetadata().get(fileID);				
					node.saveFileContent(msg.getNameOfFile(), fileID, msg.getBytesOfFile(), msg.isPrimaryServer()); 			// save the file in memory of the newly joined node
					succ.removeKey(fileID); 	 																				// remove the file key from the successor
					succ.getFilesMetadata().remove(fileID); 																	// also remove the saved file from memory
				}
			}
			
			logger.info("Finished copying file keys from successor "+ succ.getNodeName()+" to "+node.getNodeName());
		} catch (RemoteException e) {
			logger.error(e.getMessage());
		}
	}

	public void notify(NodeInterface pred_new) throws RemoteException {
		
		NodeInterface pred_old = node.getPredecessor();
		
		// if the predecessor is null accept the new predecessor
		if(pred_old == null) {
			node.setPredecessor(pred_new);		// accept the new predecessor
			return;
		}
		
		else if(pred_new.getNodeName().equals(node.getNodeName())) {
			node.setPredecessor(null);
			return;
		} else {
			BigInteger nodeID = node.getNodeID();
			BigInteger pred_oldID = pred_old.getNodeID();
			
			BigInteger pred_newID = pred_new.getNodeID();
			
			// check that pred_new is between pred_old and this node, accept pred_new as the new predecessor
			// check that ftsuccID is a member of the set {nodeID+1,...,ID-1}
			boolean cond = Util.checkInterval(pred_newID, pred_oldID.add(BigInteger.ONE), nodeID.add(BigInteger.ONE));
			if(cond) {		
				node.setPredecessor(pred_new);		// accept the new predecessor
			}	
		}		
	}

}
