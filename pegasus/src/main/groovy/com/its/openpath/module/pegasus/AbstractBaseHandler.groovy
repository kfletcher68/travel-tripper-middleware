package com.its.openpath.module.pegasus

import com.its.openpath.module.opscommon.comm.bus.IMessageBus
import com.its.openpath.module.opscommon.model.messaging.ops.OpsMessage
import com.its.openpath.module.opscommon.util.TimeUUIDUtils
import com.its.openpath.module.pegasus.xml.builder.AbstractXMLBuilder
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired

/**
 * <code>AbstractBaseHandler</code>
 * <p/>
 * Base class of all Service Request Handler classes. Contains methods common to all subclasses and the main {@link AbstractBaseHandler#execute}
 * method which acts as the main entry point and drives the workflow.
 * <p />
 * @author rajiv@itstcb.com
 * @since May 2012
 */

abstract class AbstractBaseHandler
{
  private static final Logger sLogger = LoggerFactory.getLogger( AbstractBaseHandler.class.name )

  @Autowired(required = true)
  protected IMessageBus mOpsMessageBus

  protected AbstractXMLBuilder mMessageBuilder

  
  /**
   * Publish event OpsMessage to message bus
   *<p />
   *@param uuid UUID, time base uuid generated by TimeUUIDUtils
   *@param queueName String, message bus queue name
   *@param messageType Integer, messageType from enum
   *@param source String, source of the event
   *@param destination String, destination of the event
   *@param data String, the payload for this event
   */
  def void publishEventToMessageBus(UUID uuid, String queueName, Integer messageType, String messageSubType, String source, String destination, String data)
  {
    OpsMessage msg = new OpsMessage()
    msg.messageType =messageType
    msg.messageSubType = messageSubType
    msg.source = source
    msg.destination = destination
    msg.correlationId = uuid.toString()
    msg.correlationIdBytes = TimeUUIDUtils.asByteString( uuid )
    msg.timestamp = TimeUUIDUtils.getTimeFromUUID( uuid )
    msg.data = data
    mOpsMessageBus.queueMessage(queueName, msg)
  }

  /**
   * Subclasses must implement this to perform basic validation of XML messages received from Pegasus USW. If validation
   * fails, the response to be sent back to Pegasus USW must be set in the ThreadLocal {@link InvocationContext}
   * using the associated Builder, see {@link AbstractXMLBuilder#buildErrorResponse}
   * <p />
   * @return boolean - TRUE = if received request passed validation
   */
  protected abstract boolean validate( )

  /**
   * Subclasses must implement this to process the incoming XML request from Pegasus USW. The {@link #execute} method
   * invokes this method if the validation succeeds. The XML message to be sent back to Pegasus USW must be set
   * in the ThreadLocal {@link InvocationContext} using the associated Builder,
   * see {@link AbstractXMLBuilder#buildErrorResponse}.
   * The response can be a valid response or an error message.
   */
  protected abstract void process( )


  /**
   * This is called by the {@link com.its.openpath.module.pegasus.RequestProcessorServlet} to process the
   * message received from Pegasus USW
   * <p />
   * NOTE: Subclass handlers must ensure catching and handling all exceptions and creating an error response as appropriate.
   * No Exceptions must be propergated to this level. Any uncaught exceptions at this level will result in an error
   * response sent back to USW.
   * <p />
   * @param messageType - The type of message
   * @param request - Service Request received
   * @return String - Response to be sent back to USW
   * @throws IllegalStateException - If the incoming Service Request cannot be processed
   */
  protected abstract String execute( Object messageType, String requestMessage )
  throws IllegalStateException

}