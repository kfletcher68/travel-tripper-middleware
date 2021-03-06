package com.its.openpath.module.opscommon.event.persistence

import javax.annotation.PostConstruct

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.jmx.export.annotation.ManagedResource
import org.springframework.stereotype.Service

import com.its.openpath.module.opscommon.model.messaging.ops.OpsMessage
import com.its.openpath.module.opscommon.util.PersistenceMessageBusQueueNames
import com.its.openpath.module.opscommon.util.TimeUUIDUtils

@Service("ResponseEventJSONPersistenceHandler")
@ManagedResource('OPENPATH:name=/module/opscommon/event/persistence/ResponseEventJSONPersistenceHandler')
class ResponseEventJSONPersistenceHandler extends AbstractEventPersistenceHandler
{
  private static Logger sLogger = LoggerFactory.getLogger(ResponseEventJSONPersistenceHandler.getName())
 
  /**
   * Constructor
   */
  ResponseEventJSONPersistenceHandler()
  {
    sLogger.info "Instantiated ..."
  }

  /**
   * Called by the Spring Framework after all Spring-manages beans are instantiated and wired up.
   */
  @PostConstruct
  void init( )
  {
    super.init()
    super.mOpsMessageBus.consumeFromQueue( PersistenceMessageBusQueueNames.RESPONSE_JSON_QUEUE, this, false )
  }
  
  /* (non-Javadoc)
   * @see com.its.openpath.module.opscommon.comm.bus.IMessageBusSubscriber#onMessage(java.lang.String, com.its.openpath.module.opscommon.model.messaging.ops.OpsMessage)
   */
  @Override
  public void onMessage( String topic, OpsMessage message )
  throws RuntimeException
  {
    UUID uuid = TimeUUIDUtils.toUUID ( message.getCorrelationIdBytes ().toByteArray () )
    String payload = message.getData ()
    /*
     * Persist byte [], not the string as byte [] is more compact
     */
    this.persistPayload ( uuid, "responsepayloadjson", payload?payload.bytes: "".getBytes ())
  }
  
  @Override
  public String getSubscriberId()
  {
    return this.class.name;
  }

}
