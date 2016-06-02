package com.its.openpath.module.pegasus.xml.builder

import com.dyuproject.protostuff.JsonIOUtil
import com.its.openpath.module.opscommon.model.messaging.ops.AvailabilityRatePlan
import com.its.openpath.module.opscommon.model.messaging.ops.AvailabilityRequest
import com.its.openpath.module.opscommon.model.messaging.ops.AvailabilityResponse
import com.its.openpath.module.opscommon.model.messaging.ops.GuestInfo
import com.its.openpath.module.opscommon.model.messaging.ops.OpsErrorCode
import com.its.openpath.module.opscommon.model.messaging.ops.PriceRange
import com.its.openpath.module.opscommon.model.messaging.ops.ProductType
import com.its.openpath.module.opscommon.model.messaging.ops.RoomAmenity
import com.its.openpath.module.opscommon.model.messaging.ops.RoomAvailabilityRequest
import com.its.openpath.module.opscommon.model.messaging.ops.RoomAvailabilitySearchType
import com.its.openpath.module.opscommon.model.messaging.ops.RoomStay
import com.its.openpath.module.opscommon.model.messaging.ops.Search
import com.its.openpath.module.opscommon.model.messaging.ops.SearchCriteria
import com.its.openpath.module.opscommon.model.messaging.ops.Source
import com.its.openpath.module.opscommon.model.messaging.ops.SourceChannel
import com.its.openpath.module.opscommon.model.messaging.ops.Stay
import com.its.openpath.module.opscommon.util.InvocationContext
import groovy.json.JsonSlurper
import groovy.xml.MarkupBuilder
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * <code>XMLSinglePropertyAvailabilityMessageBuilder</code>
 * <p/>
 * The {@link com.its.openpath.module.pegasus.xml.handler.XMLSinglePropertyAvailabilityRequestHandler} invokes this Builder
 * to build a JSON Availability Management Service Request Messages to be sent to OpenPath and build an OTA XML message to be
 * sent to Pegasus USW using the Service Response Message received from OpenPath.
 * <p />
 * @author rajiv@itstcb.com
 * @since May 2012
 */

@Service('XMLSinglePropertyAvailabilityMessageBuilder')
class XMLSinglePropertyAvailabilityMessageBuilder
extends AbstractXMLBuilder
{
  private static Logger sLogger = LoggerFactory.getLogger( XMLSinglePropertyAvailabilityMessageBuilder.class.name )


  /**
   * @see {@link com.its.openpath.module.pegasus.AbstractBaseBuilder#buildRequestToOpenPathFromExternal}
   */
  def boolean buildRequestToOpenPathFromExternal( )
  {
    InvocationContext context = InvocationContext.instance
    def ROOT = context.externalSystemRequestData
    def OTA_HotelAvailRQ = ROOT.Body.OTA_HotelAvailRQ
    AvailabilityRequest avlRequest = new AvailabilityRequest()
    Writer writer = new StringWriter()

    try
    {
      // Basic info
      avlRequest.productType = ProductType.HOTEL_ROOM
      avlRequest.requestData = new RoomAvailabilityRequest()
      avlRequest.requestData.searchType = RoomAvailabilitySearchType.SINGLE_PROPERTY
      avlRequest.requestData.extSysRefId = OTA_HotelAvailRQ.@EchoToken
      avlRequest.requestData.extSysTimestamp = OTA_HotelAvailRQ.@TimeStamp
      avlRequest.requestData.multipartRequest = OTA_HotelAvailRQ.@TransactionStatusCode = "" ? false : true
      avlRequest.requestData.primaryLangId = "${OTA_HotelAvailRQ.@PrimaryLanguageID}"
      avlRequest.requestData.requireRateDetails = OTA_HotelAvailRQ.@RateDetailsInd.toBoolean()
      avlRequest.requestData.requestedCurrency = "${OTA_HotelAvailRQ.@RequestedCurrency}"
      avlRequest.requestData.exactMatchOnly = OTA_HotelAvailRQ.@ExactMatchOnly

      Source source = new Source()
      source.id = OTA_HotelAvailRQ.POS.Source[0].RequestorID.@ID
      source.type = OTA_HotelAvailRQ.POS.Source[0].RequestorID.@Type
      source.description = OTA_HotelAvailRQ.POS.Source[0].RequestorID.@ID_Context
      source.countryCode = OTA_HotelAvailRQ.POS.Source[0].@ISOCountry
      source.pseudoCityCode = OTA_HotelAvailRQ.POS.Source[0].@PseudoCityCode

      List<SourceChannel> channelList = new ArrayList<SourceChannel>()
      SourceChannel sourceChannel_1 = new SourceChannel()
      sourceChannel_1.type = OTA_HotelAvailRQ.POS.Source[1].BookingChannel.@Type
      sourceChannel_1.id = OTA_HotelAvailRQ.POS.Source[1].BookingChannel.CompanyName.@Code
      sourceChannel_1.name = OTA_HotelAvailRQ.POS.Source[1].BookingChannel.CompanyName.@CodeContext
      channelList.add( sourceChannel_1 )
      SourceChannel sourceChannel_2 = new SourceChannel()
      sourceChannel_2.type = OTA_HotelAvailRQ.POS.Source[2].BookingChannel.@Type
      sourceChannel_2.id = OTA_HotelAvailRQ.POS.Source[2].BookingChannel.CompanyName.@Code
      sourceChannel_2.name = OTA_HotelAvailRQ.POS.Source[2].BookingChannel.CompanyName.@CodeContext
      channelList.add( sourceChannel_2 )
      source.channelInfoList = channelList
      avlRequest.requestData.source = source

      // Stay and Search info
      populateStayInfoInOpsRequest( OTA_HotelAvailRQ, avlRequest.requestData )
      populateSearchInfoInOpsRequest( OTA_HotelAvailRQ, avlRequest.requestData )

      JsonIOUtil.writeTo( writer, avlRequest, AvailabilityRequest.getSchema(), false );
    }
    catch ( Throwable e )
    {
      def errMsg = "PEGASUS - Couldn't build an Single Prop Avl Service Request to be sent to OpenPath: [${context.getSessionDataItem( "TXN_REF" )}]"
      sLogger.error errMsg, e
      buildErrorResponse( OpsErrorCode.SERVICE_REQUEST_PROCESSING_ERROR, errMsg )
      return false
    }

    context.openPathRequestData = writer.toString()
    if ( sLogger.debugEnabled )
    {
      sLogger.debug "PEGASUS - Built OPS Avl Req for: [${context.getSessionDataItem( "TXN_REF" )}]"
    }

    return true
  }

  /**
   * Populate Stay info in the OPS Room Availability Request.
   * <p >
   * @param OTA_HotelAvailRQ - XML OTA Messsage
   * @param request - Request message to populate
   * @throws RuntimeException - If the message cannot be populated
   */
  def private populateStayInfoInOpsRequest( def OTA_HotelAvailRQ, RoomAvailabilityRequest request )
  {
    try
    {
      Stay stay = new Stay()
      request.stay = stay
      stay.start = OTA_HotelAvailRQ.AvailRequestSegments.AvailRequestSegment.StayDateRange.@Start
      stay.duration = OTA_HotelAvailRQ.AvailRequestSegments.AvailRequestSegment.StayDateRange.@Duration
      stay.end = OTA_HotelAvailRQ.AvailRequestSegments.AvailRequestSegment.StayDateRange.@End
      stay.roomCount = OTA_HotelAvailRQ.AvailRequestSegments.AvailRequestSegment[0].RoomStayCandidates.RoomStayCandidate[0].@Quantity
      stay.roomCategory = OTA_HotelAvailRQ.AvailRequestSegments.AvailRequestSegment[0].RoomStayCandidates.RoomStayCandidate[0].@RoomCategory

      List<AvailabilityRatePlan> ratePlans = new ArrayList<AvailabilityRatePlan>()
      stay.ratePlansList = ratePlans
      OTA_HotelAvailRQ.AvailRequestSegments.AvailRequestSegment[0].RatePlanCandidates.children().each {
        AvailabilityRatePlan ratePlan = new AvailabilityRatePlan()
        ratePlan.code = it.@RatePlanCode
        ratePlan.category = it.@RatePlanCategory
        ratePlan.id = it.@RatePlanID
        ratePlan.commissionable = it.RatePlanCommission.@ComissionableInd
        ratePlans.add( ratePlan )
      }

      List<RoomAmenity> amenitiesList = new ArrayList<RoomAmenity>()
      stay.amenitiesList = amenitiesList
      List<GuestInfo> guestInfoList = new ArrayList<GuestInfo>()
      stay.guestInfoList = guestInfoList
      OTA_HotelAvailRQ.AvailRequestSegments.AvailRequestSegment[0].RoomStayCandidates.children().each { def roomStay ->
        roomStay.RoomAmenity.findAll { it.@RoomAmenity }.each { def amenity ->
          RoomAmenity roomAmenity = new RoomAmenity()
          roomAmenity.id = amenity.@RoomAmenity
          roomAmenity.quantity = amenity.@Quantity
          amenitiesList.add( roomAmenity )
        }
        roomStay.GuestCounts.children().each { def guest ->
          GuestInfo guestInfo = new GuestInfo()
          guestInfo.ageQualifier = guest.@AgeQualifyingCode
          guestInfo.count = guest.@Count
          guestInfoList.add( guestInfo )
        }
      }
    }
    catch ( Throwable e )
    {
      throw new RuntimeException( "PEGASUS - Couldn't populate Stay info in the Room Availability Request", e )
    }
  }

  /**
   * Populate Search info in the OPS Room Availability Request.
   * <p >
   * @param OTA_HotelAvailRQ - XML OTA Messsage
   * @param request - Request message to populate
   * @throws RuntimeException - If the message cannot be populated
   */
  def private populateSearchInfoInOpsRequest( def OTA_HotelAvailRQ, RoomAvailabilityRequest request )
  throws RuntimeException
  {
    try
    {
      Search search = new Search()
      request.search = search

      List<SearchCriteria> searchCriteriaList = new ArrayList<SearchCriteria>()
      search.criteriaList = searchCriteriaList
      OTA_HotelAvailRQ.AvailRequestSegments.AvailRequestSegment[0].HotelSearchCriteria.Criterion.HotelRef.findAll { it.@HotelCode }.each { def hotelRef ->
        SearchCriteria criteria = new SearchCriteria()
        criteria.groupCode = hotelRef.@ChainCode
        criteria.itemCode = hotelRef.@HotelCode
        criteria.itemContext = hotelRef.@HotelCodeContext
        searchCriteriaList.add( criteria )
      }

      PriceRange priceRange = new PriceRange()
      search.priceRange = priceRange
      priceRange.min = OTA_HotelAvailRQ.AvailRequestSegments.AvailRequestSegment[0].HotelSearchCriteria.Criterion.RateRange.@MinRate
      priceRange.max = OTA_HotelAvailRQ.AvailRequestSegments.AvailRequestSegment[0].HotelSearchCriteria.Criterion.RateRange.@MaxRate
      priceRange.currencyCode = OTA_HotelAvailRQ.AvailRequestSegments.AvailRequestSegment[0].HotelSearchCriteria.Criterion.RateRange.@CurrencyCode
      priceRange.priceIndicator = OTA_HotelAvailRQ.AvailRequestSegments.AvailRequestSegment[0].HotelSearchCriteria.Criterion.RateRange.@RateMode
      search.isReturnUnavailableItems = true
    }
    catch ( Throwable e )
    {
      throw new RuntimeException( "Couldn't populate Search info in the Room Availability Request", e )
    }
  }

  /**
   * @see {@link com.its.openpath.module.pegasus.AbstractBaseBuilder#buildResponseToExternalFromOpenPath}
   */
  def boolean buildResponseToExternalFromOpenPath( )
  {
    InvocationContext context = InvocationContext.instance
    AvailabilityResponse avlResponse = new AvailabilityResponse()

    try
    {
      sLogger.debug "PEGASUS - Single Property Availability Response received from OPS BUS: \n${context.openPathResponseData}"

      JsonIOUtil.mergeFrom( ((String) context.openPathResponseData).bytes, avlResponse, AvailabilityResponse.schema, false )
    }
    catch ( Throwable e )
    {
      def errMsg = "PEGASUS - Couldn't deserialize the JSON Availability Management Response received from OpenPath, ${context.getSessionDataItem( "TXN_REF" )}"
      sLogger.error errMsg, e
      buildErrorResponse( OpsErrorCode.SERVICE_RESPONSE_PROCESSING_ERROR, errMsg )
      return false
    }

    if ( avlResponse.errorResponse )
    {
      // TODO handle all errors in the list
      buildErrorResponse( avlResponse.errorResponse.errorMessagesList[0].errorCode,
        avlResponse.errorResponse.errorMessagesList[0].errorMessage )
    }
    else
    {
      buildResponseToPegasus( avlResponse )
    }

    return true
  }

  /**
   * Helper method to build the OTA XML Response message to be sent back to Pegasus USW.
   * <p />
   * @param avlResponse - Message received from OpenPath
   */
  def private buildResponseToPegasus( AvailabilityResponse avlResponse )
  {
    InvocationContext context = InvocationContext.instance
    def writer = new StringWriter()

    def jsonSlurper = new JsonSlurper()
    List<RoomStay> roomStayList = avlResponse.responseData.roomStaysList

    try
    {
      def markupBuilder = new MarkupBuilder( writer )
      markupBuilder.omitEmptyAttributes = true
      markupBuilder.expandEmptyElements = false
      markupBuilder.omitNullAttributes = true
      // SOAP Envelope
      markupBuilder.'SOAP-ENV:Envelope'( 'xmlns:SOAP-ENV': 'http://schemas.xmlsoap.org/soap/envelope/' ) {
        // SOAP Header
        markupBuilder.'SOAP-ENV:Header'( 'xmlns:wsa': 'http://schemas.xmlsoap.org/ws/2004/08/addressing',
          'xmlns:wsse': 'http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd' )

        // SOAP Body
        markupBuilder.'SOAP-ENV:Body' {
          // OTA Response
          markupBuilder.'OTA_HotelAvailRS'( super.getOTARootElementAttributesInResponse( 'OTA_HotelAvailRQ' ) ) {
            super.buildPosElementInResponse( markupBuilder )
            if ( avlResponse.responseData.isSuccess )
            {
              Success()
            }

            RoomStays {
              roomStayList.each {
                StringWriter writer1 = new StringWriter()
                JsonIOUtil.writeTo( writer1, it, it.cachedSchema(), false );
                def roomStayJson = (jsonSlurper.parseText( writer1.toString() ))
                RoomStay( AvailabilityStatus: roomStayJson.availabilityStatus, ResponseType: 'PropertyRateList' ) {
                  populateRoomStayInfoOnPegsResponse( markupBuilder, roomStayJson )
                }
              }
            }
          } // OTA Response
        } // SOAP Body
      } // SOAP Envelope
    }
    catch ( Throwable e )
    {
      def errMsg = "Couldn't build the Availability XML Response to be sent to Pegasus: [${context.getSessionDataItem( "TXN_REF" )}] "
      sLogger.error errMsg, e
      buildErrorResponse( OpsErrorCode.SERVICE_RESPONSE_PROCESSING_ERROR, errMsg )
      return
    }

    def responseStr = writer.toString()
    responseStr = responseStr.replace( '2.001', '4.001' )
    context.success = true
    context.externalSystemResponseData = responseStr
    if ( sLogger.debugEnabled )
    {
      sLogger.debug "PEGASUS - Built USW Avl Rsp for: [${context.getSessionDataItem( "TXN_REF" )}]"
    }
  }

  /**
   * Helper method to populate each Room Stay info in the Room Stay section of the OTA XML response to Pegasus USW.
   * <p />
   * @param builder - Markup Builder instance to use
   * @param roomStay - Room Stay info rcvd from OPS
   */
  def private populateRoomStayInfoOnPegsResponse = { MarkupBuilder builder, def roomStayJson ->
    builder.RoomTypes() {
      roomStayJson.rooms.each { def room ->
        // Populate Room types
        RoomType( RoomTypeCode: room?.typeId, NumberOfUnits: room?.numberOfUnits,
          RoomCategory: room?.category, AdultOccupancyMatchType: room?.adultOccupancyMatchType,
          ChildOccupancyMatchType: room?.childOccupancyMatchType, BedTypeMatchType: room?.bedTypeMatchType ) {

          RoomDescription {
            Text( room?.description )
          }

          room.amenities?.each { def amenity ->
            Amenity( RoomAmenity: amenity?.id, ExistsCode: amenity?.existsCode, Quantity: amenity?.quantity,
              IncludedInRateIndicator: amenity?.includedInRate, ConfirmableInd: amenity?.needConfirmation )
          }
        } // RoomType
      } // Each Room
    } // Room Types

    // Populate Rate Plans
    builder.RatePlans {
      roomStayJson?.ratePlans?.each { def ratePlan ->
        RatePlan( RatePlanCode: ratePlan?.code, AvailabilityStatus: ratePlan?.availabilityStatus,
          EffectiveDate: ratePlan?.effectiveDate, ExpireDate: ratePlan?.expiryDate, RatePlanId: ratePlan?.id,
          RatePlanName: ratePlan?.description, MarketCode: ratePlan?.marketCode, ID_RequiredInd: ratePlan?.idRequiredAtCheckin ) {
          RatePlanDescription {
            Text( ratePlan?.promotionalText )
          }
          ratePlan.inclusions.each { def inclusion ->
            RatePlanInclusions {
              RatePlanInclusionDescription() {
                Text( inclusion?.description ) {
                }
              }
            }
          } // Each Rate Plan inclusion
        } // Rate Plan
      } // Each Rate Plan
    } // Rate Plans

    // Populate Room Rates
    populateRoomRates( builder, roomStayJson )

    builder.GuestCounts {
      roomStayJson?.guestCounts?.each { def guest ->
        GuestCount( AgeQualifyingCode: guest?.ageQualifier, Count: guest?.count ) {}
      }
    }

    builder.TimeSpan( Start: roomStayJson?.timeSpan?.start, Duration: roomStayJson?.timeSpan?.duration, End: roomStayJson?.timeSpan?.end ) {}

    def propertyInfo = roomStayJson.basicPropertyInfo
    builder.BasicPropertyInfo( ChainCode: propertyInfo?.chainCode, HotelCode: propertyInfo?.hotelCode,
      HotelName: propertyInfo?.name, CityName: propertyInfo?.city, StateProv: propertyInfo?.state, CountryName: propertyInfo?.country ) {
      propertyInfo?.hotelAmenities?.each { def hotelAmenity ->
        HotelAmenity( Code: hotelAmenity?.id, IncludedInRateIndicator: hotelAmenity?.includedInRate, ConfirmableInd: hotelAmenity?.confirmable ) {
          Description{
            Text( hotelAmenity?.description )
          }
        }
      }
    }
  }

  /**
   * Helper method to populate Room Rates in the Room Stay section of the OTA XML response to Pegasus USW.
   * <p />
   * @param builder - Markup Builder instance to use
   * @param roomStay - Room Stay info rcvd from OPS
   */
  private def populateRoomRates( MarkupBuilder builder, def roomStay )
  {
    builder.RoomRates {
      roomStay.roomStayRates.each { def roomStayRate ->
        RoomRate( RoomTypeCode: roomStayRate?.roomTypeCode, NumberOfUnits: roomStayRate?.numberOfUnits,
          RatePlanCode: roomStayRate?.ratePlanCode, BookingCode: roomStayRate?.bookingCode,
          AvailabilityStatus: roomStayRate?.availabilityStatus ) {
          Rates {
            roomStayRate.rates.each { def rate ->
              Rate( RateTimeUnit: rate?.rateTimeOfUnit, MinLOS: rate?.minLOS, MaxLOS: rate?.maxLOS, RateMode: rate?.rateMode,
                EffectiveDate: rate.effectiveDate ) {
                Base( AmountBeforeTax: rate?.baseRate?.amountBeforeTax, AmountAfterTax: rate?.baseRate?.amountAfterTax,
                  CurrencyCode: rate?.baseRate?.currencyCode, DecimalPlaces: rate?.baseRate?.decimalPlaces )

                rate.additionalGuestFees.each { def guestFee ->
                  AdditionalGuestAmounts {
                    AdditionalGuestAmount( AgeQualifyingCode: guestFee?.ageQualifier ) {
                      Amount( AmountAfterTax: guestFee?.amountAfterTax, AmountBeforeTax: guestFee?.amountBeforeTax,
                        DecimalPlaces: guestFee?.decimalPlaces, CurrencyCode: guestFee?.currencyCode )
                    } // AdditionalGuestAmount
                  } // AdditionalGuestAmounts
                } // Each additional guest fee

                rate.additionalFees.each { def fee ->
                  Fees {
                    Fees( Code: fee.id, Amount: fee.amount, CurrencyCode: fee.currencyCode ) {}
                  }
                } // Each additional fee

                CancelPolicies {
                  CancelPenalty {
                    rate?.cancelPenalties?.each { def penalty ->
                      Deadline( OffsetUnitMultiplier: penalty?.deadline?.multiplier, OffsetTimeUnit: penalty?.deadline?.timeUnit,
                        OffsetDropTime: penalty?.deadline?.qualifier ) {}
                      AmountPercent( Amount: penalty?.amount, Percent: penalty?.amountPercentage, NumbrOfNights: penalty?.numberOfNights,
                        BasisType: penalty?.basistype, TaxInclusive: penalty?.taxInclusive, FeesInclusive: penalty?.feesInclusive ) {}
                      PenaltyDescription {
                        Text( penalty?.description )
                      }
                    } // each cancel penalty
                  } // CancelPenalty
                } // CancelPolicies


                PaymentPolicies {
                  def guaranteePayment = rate?.paymentPolicies?.guaranteePayment
                  GuaranteePayment( GuaranteeType: guaranteePayment?.type, NonRefundableIndicator: guaranteePayment?.nonRefundable ) {
                    AcceptedPayments {
                      def acceptablePayments = guaranteePayment?.acceptablePayments
                      acceptablePayments?.each { def payment ->
                        AcceptedPayment( PaymentTransactionTypeCode: payment?.id ) {
                          PaymentCard( CardCode: payment?.code )
                        }
                      } // each payment type
                      AmountPercent( Amount: guaranteePayment?.amount?.value, CurrencyCode: guaranteePayment?.amount?.currencyCode ) {}
                    } // AcceptedPayment
                  } // GuaranteePayment
                } // PaymentPolicies

                RateDescription {
                  Text( rate.description ) {}
                }
              } // Each rate
            } // Rate
          } // Rates

          RoomRateDescription {
            Text( roomStayRate?.description ) {}
          }

          def total = roomStayRate.total
          Total( AmountBeforeTax: total?.amountBeforeTax, AmountAfterTax: total?.amountAfterTax, CurrencyCode: total?.currencyCode,
            AdditionalFeesExcludedIndicator: total?.additionalFeesExcluded ) {
            Taxes {
              total?.taxes?.each() { def tax ->
                Taxes( Code: tax?.taxCode, Percent: tax?.percent, ChargeUnit: tax?.chargeUnit, Amount: tax?.amount ) {}
              }
            }
          }

        } // RoomRate
      } // Each room stay
    } // RoomRates
  }

}
