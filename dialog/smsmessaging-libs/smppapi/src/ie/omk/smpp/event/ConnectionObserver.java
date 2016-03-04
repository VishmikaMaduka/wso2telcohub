package ie.omk.smpp.event;

import ie.omk.smpp.Connection;
import ie.omk.smpp.message.SMPPPacket;

/**
 * A connection observer implementation is used to receive event updates from an
 * Connection object. If an application wishes to use asynchronous
 * communications mode with the API framework, it will need to provide an
 * implementation of this interface to the Connection to be notified of SMPP
 * events (such as packet reception).
 * 
 * @author Oran Kelly
 * @version $Id: ConnectionObserver.java 255 2006-03-09 09:34:37Z orank $
 * @see Connection#addObserver
 */
public interface ConnectionObserver {
    /**
     * Called when a new SMPP packet has been received from the SMSC. This
     * method is called by the API framework whenever an SMPP packet has been
     * read and decoded from the network connection to the SMSC. Identification
     * of the packet type can be achieved by calling
     * {@link SMPPPacket#getCommandId}.
     * 
     * @param source
     *            the Connection which received the packet.
     * @param packet
     *            the SMPP packet received.
     */
    void packetReceived(Connection source, SMPPPacket packet);

    /**
     * Called for all events <b>other </b> than packet reception. This method is
     * called for all events generated by the API framework <i>except </i> that
     * of a packet received. The {@link #packetReceived}method is called in
     * that case. The <code>update</code> method is mostly used for control
     * events, such as signifying the exit of the receiver thread or notifying
     * of error conditions.
     * 
     * @param source
     *            the Connection which received the packet.
     * @param event
     *            the SMPP event type.
     * @see ie.omk.smpp.event.SMPPEvent#getType
     */
    void update(Connection source, SMPPEvent event);
}
