package com.its.openpath.module.opsari.reztrip.handler

import javax.annotation.PostConstruct

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.jmx.export.annotation.ManagedResource
import org.springframework.stereotype.Service

import com.dyuproject.protostuff.JsonIOUtil
import com.its.openpath.module.opscommon.comm.bus.IMessageBusSubscriber
import com.its.openpath.module.opscommon.model.messaging.ops.AvailabilityRequest
import com.its.openpath.module.opscommon.model.messaging.ops.OpsErrorCode
import com.its.openpath.module.opscommon.model.messaging.ops.OpsMessage
import com.its.openpath.module.opscommon.util.InvocationContext
import com.its.openpath.module.opscommon.util.TimeUUIDUtils

/**
 * <code>InventoryManagementRequestHandler</code>
 * <p/>
 * Implements the Inventory Management Service Provider functionality by interfacing with the RezTrip CRS.
 * Processes Service Requests received on a known Queue on the OPS Message Bus; Requests are expected to be JSON
 * encoded and are POSTed to a REST Endpoint provided by RezTrip. The Response from RezTrip
 * is published on a temporary Queue on the OPS Message bus so that the module that generated the Inventory Management
 * Request can consume it.
 * <p />
 * @author rajiv@itstcb.com
 * @since May 2012
 */

@Service("InventoryManagementRequestHandler")
@ManagedResource('OPENPATH:name=/module/opsari/reztrip/handler/InventoryManagementRequestHandler')
class InventoryManagementRequestHandler
extends AbstractServiceRequestHandler
implements IMessageBusSubscriber
{
  private static final Logger sLogger = LoggerFactory.getLogger( InventoryManagementRequestHandler.class.name )


  /**
   * Constructor
   */
  InventoryManagementRequestHandler( )
  {
    sLogger.info "Instantiated ..."
  }

  /**
   * Called by the Spring Framework after all Spring-manages beans are instantiated and wired up.
   */
  @PostConstruct
  void init( )
  {
    mOpsMessageBus.consumeFromQueue( "AVAILABILITY_REQ", this, false )
  }

  /**
   * @see {@link IMessageBusSubscriber#getSubscriberId}
   */
  String getSubscriberId( )
  {
    return this.class.name
  }

  /**
   * @see {@link IMessageBusSubscriber#onMessage}
   */
  void onMessage( final String s, final OpsMessage opsMessage )
  {
    def requestJSON = opsMessage.data

    def responseJSON = null
    AvailabilityRequest requestObj = new AvailabilityRequest()
    InvocationContext context

    sLogger.debug "*** OPSARI - Rcvd a Req from OPS Bus ..."
    sLogger.debug "~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~"
    if ( sLogger.isDebugEnabled() )
    {
      sLogger.debug "*** OPSARI - The new Service Request received is:\n [$requestJSON]"
    }

    UUID uuid = TimeUUIDUtils.toUUID( opsMessage.getCorrelationIdBytes().toByteArray() )

    // Deserialize the JSON request to its object form
    try
    {
      JsonIOUtil.mergeFrom( requestJSON.bytes, requestObj, AvailabilityRequest.schema, false )
      // Create the OpenPath Context container and set it as a ThreadLocal so that it's available downstream
      context = InvocationContext.getNewInstance( uuid )
      def TXN_REF = "CorRelId: [${context.correlationId}]"
      context.addSessionDataItem( "TXN_REF", TXN_REF )
    }
    catch ( Throwable e )
    {
      responseJSON = super.logAndBuildErrorResponse( requestObj.productType, "OPSARI - Couldn't parse the OPS JSON request received; ", e,
        OpsErrorCode.SERVICE_REQUEST_FORMAT_ERROR )
      super.publishResponseOnOpsBus( responseJSON, opsMessage )
    }

    responseJSON = super.postAvlRequestToRezTripCRS( requestJSON, requestObj, handleResponseFromRezTrip ) { String endpointURL, int httpStatusCode, Throwable exception ->
      def errorMessage = "OPSARI - Couldn't POST Service Request: [${context.getSessionDataItem( "TXN_REF" )}] to RezTrip REST Endpoint: [${endpointURL}]; " +
        "HTTP status Code: ${httpStatusCode ?: 'N/A'}"
      sLogger.error "${errorMessage}"
      sLogger.error "*************************************************************************************************"
      if ( !exception )
      {
        sLogger.error "*** HTTP Error Code Rcvd: ${httpStatusCode}"
        sLogger.error "*** Request to be sent: ${requestJSON}"
      }

      OpsErrorCode errorCode = OpsErrorCode.SERVICE_RESPONSE_PROCESSING_ERROR
      switch ( httpStatusCode )
      {
        case 500:
          errorCode = OpsErrorCode.SERVICE_RESPONSE_ENDPOINT_UNAVAILABLE
          break
      }

      return super.logAndBuildErrorResponse( requestObj.productType, errorMessage, exception, errorCode )
    }
    super.publishResponseOnOpsBus( responseJSON, opsMessage )
  }

  /**
   * Closure block invoked to process the response received from the CRS, if needed to.
   */
  Closure handleResponseFromRezTrip = {  String responseJSON ->

    if ( sLogger.isDebugEnabled() )
    {
      sLogger.debug "OPSARI - Response received from RezTrip CRS: \n ${responseJSON}"
    }
    // Not doing anything with the response yet
    return responseJSON
  }

}
