package com.signal.logic;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sip.*;
import javax.sip.address.Address;
import javax.sip.address.AddressFactory;
import javax.sip.header.CallIdHeader;
import javax.sip.header.ContactHeader;
import javax.sip.header.ContentTypeHeader;
import javax.sip.header.HeaderFactory;
import javax.sip.message.MessageFactory;
import javax.sip.message.Request;
import javax.sip.message.Response;

/**
 * @class public class ResponseManager
 * @brief 응답 처리 클래스
 */
public class ResponseManager {
    /* 출력 레벨에 따라 지정한 데이터를 표준 출력 */
    private static final Logger logger = LoggerFactory.getLogger(ResponseManager.class);
    /* 응답 관리 매니저(singleton) */
    public static ResponseManager responseManager = null;

    /**
     * @fn private ResponseManager()
     * @brief 응답 관리 매니저 객체를 초기화하는 함수
     */
    private ResponseManager() {
    }

    ////////////////////////////////////////////////////////////////////////////////////////
    /// @ Public Functions
    ////////////////////////////////////////////////////////////////////////////////////////

    /**
     * @fn public static ResponseManager getInstance()
     * @brief 응답 관리 매니저의 싱글턴 인스턴스를 반환하는 함수
     * @return 응답 관리 매니저
     */
    public static ResponseManager getInstance() {
        if (responseManager == null) responseManager = new ResponseManager();
        return responseManager;
    }

    /**
     * @fn public void respondToInvite(final RequestEvent requestEvent, final ServerTransaction serverTransaction, final MessageFactory messageFactory, final AddressFactory addressFactory, final HeaderFactory headerFactory, final SipProvider SipProvider, int port, final SipCall sipCall)
     * @brief Invite 요청에 응답하는 함수
     * @param requestEvent      요청 이벤트(입력, 읽기 전용)
     * @param serverTransaction 서버 트랜잭션(입력, 읽기 전용)
     * @param messageFactory    SIP 메시지 인터페이스(입력, 읽기 전용)
     * @param addressFactory    SIP 메시지 주소 인터페이스(입력, 읽기 전용)
     * @param headerFactory     SIP 메시지 헤더 인터페이스(입력, 읽기 전용)
     * @param port              포트 번호(입력)
     * @param sipCall           SIP 호 관리 객체(입력, 읽기 전용)
     * @return 반환값 없음
     */
    public void respondToInvite(final RequestEvent requestEvent, final ServerTransaction serverTransaction, final MessageFactory messageFactory, final AddressFactory addressFactory, final HeaderFactory headerFactory, int port, final SipCall sipCall) {
        if(port <= 0) throw new NullPointerException("Parameter Error");
        SipCall.checkObjectNull(null, requestEvent, serverTransaction, messageFactory, addressFactory, headerFactory, sipCall);

        Request request = requestEvent.getRequest();
        try {
            // Get Or New Dialog
            Dialog dialog = SipCall.getDialogFromRequestEvent(requestEvent, serverTransaction);
            SipCall.checkObjectNull("Fail to create Dialog", dialog);

            // Get Call-ID Header from New Dialog
            CallIdHeader callIdHeader = dialog.getCallId();

            // 기존에 Invite 가 존재하면 새로운 Invite 에 대해 491 Request Pending
            if (SipCall.searchRequestFromTransactionHashMap(callIdHeader, Request.INVITE) != null) {
                logger.debug("491 Request Pending Response is sent");
                ResponseManager.getInstance().respondWith4xx(serverTransaction, messageFactory, Response.REQUEST_PENDING);
                return;
            }

            // 기존에 Session 이 진행 중이면 새로운 Invite 에 대해 486 Busy Here -> Dialog 한 번에 하나만 허용
            if (SipCall.getDialogHashMap().size() > 0) {
                logger.debug("486 Busy Here Response is sent");
                ResponseManager.getInstance().respondWith4xx(serverTransaction, messageFactory, Response.BUSY_HERE);
                return;
            }

            // Add Transaction
            SipCall.addTransactionHashMap(callIdHeader, serverTransaction);

            // 100 Trying
            ResponseManager.getInstance().respondWith1xxToInvite(request, serverTransaction, messageFactory, Response.TRYING);

            // 180 Ringing
            ResponseManager.getInstance().respondWith1xxToInvite(request, serverTransaction, messageFactory, Response.RINGING);

            // 200 OK
            ResponseManager.getInstance().respondWith200ToInvite(request, serverTransaction, messageFactory, headerFactory, addressFactory);

            // Add Dialog & Remove Transaction
            SipCall.removeTransactionHashMap(callIdHeader);
            SipCall.addDialogHashMap(callIdHeader, dialog);

            logger.debug("Invite Call-ID : {}", callIdHeader);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * @fn public void respondToBye(final Request request, final ServerTransaction serverTransaction, final MessageFactory messageFactory)
     * @brief Bye 요청에 응답하는 함수
     * @param request           요청(입력, 읽기 전용)
     * @param serverTransaction 서버 트랜잭션(입력, 읽기 전용)
     * @param messageFactory    SIP 메시지 인터페이스(입력, 읽기 전용)
     * @return 반환값 없음
     */
    public void respondToBye(final Request request, final ServerTransaction serverTransaction, final MessageFactory messageFactory) {
        SipCall.checkObjectNull(null, request, serverTransaction, messageFactory);

        try {
            CallIdHeader callIdHeader = serverTransaction.getDialog().getCallId();
            logger.debug("Bye Call-ID : {}", callIdHeader);

            // Find Dialog & New Response
            Response response;
            if (SipCall.findDialogHashMap(callIdHeader)) { // 481 Call/Transaction Does Not Exist
                response = messageFactory.createResponse(Response.CALL_OR_TRANSACTION_DOES_NOT_EXIST, request);
                logger.debug("Call/Transaction Does Not Exist");
            } else { // 200 OK
                response = messageFactory.createResponse(Response.OK, request);
            }
            SipCall.checkObjectNull("Fail to create new response", response);

            // Add Transaction
            SipCall.addTransactionHashMap(callIdHeader, serverTransaction);

            // Send
            serverTransaction.sendResponse(response);

            // Remove Transaction & Dialog
            SipCall.removeTransactionHashMap(callIdHeader);
            SipCall.removeDialogHashMap(callIdHeader);
            logger.debug("@ Response : \n{}", response);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * @fn public String respondToTimeout(final TimeoutEvent timeoutEvent, final MessageFactory messageFactory)
     * @brief 시간 초과 이벤트에 응답하는 함수(서버 트랜잭션에서 발생하는 시간 초과)
     * @param timeoutEvent   시간 초과 이벤트(입력, 읽기 전용)
     * @param messageFactory SIP 메시지 인터페이스(입력, 읽기 전용)
     * @return 시간 초과가 발생한 요청의 Method 이름
     */
    public String respondToTimeout(final TimeoutEvent timeoutEvent, final MessageFactory messageFactory) {
        SipCall.checkObjectNull(null, timeoutEvent, messageFactory);

        String methodName = null;

        try {
            // Get Server Transaction
            ServerTransaction serverTransaction = timeoutEvent.getServerTransaction();
            SipCall.checkObjectNull("Fail to get Server Transaction", serverTransaction);

            // Get Request & Method Name
            Request request = serverTransaction.getRequest();
            methodName = serverTransaction.getRequest().getMethod();

            // New Timeout Response
            Response response = messageFactory.createResponse(Response.REQUEST_TIMEOUT, request);
            SipCall.checkObjectNull("Fail to create new response", response);

            // Send
            serverTransaction.sendResponse(response);
            logger.debug("@ Response : \n{}", response);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return methodName;
    }

    /**
     * @fn public void respondWith2xxToNonInviteReq(final Request request, final ServerTransaction serverTransaction, final MessageFactory messageFactory, int responseType)
     * @brief Non-invite 요청에 2xx 로 응답하는 함수
     * @param request           요청(입력, 읽기 전용)
     * @param serverTransaction 서버 트랜잭션(입력, 읽기 전용)
     * @param messageFactory    SIP 메시지 인터페이스(입력, 읽기 전용)
     * @param responseType      응답 유형(입력)
     * @return 반환값 없음
     */
    public void respondWith2xxToNonInviteReq(final Request request, final ServerTransaction serverTransaction, final MessageFactory messageFactory, int responseType) {
        SipCall.checkObjectNull(null, request, serverTransaction, messageFactory);

        try {
            // New Response
            Response response = messageFactory.createResponse(responseType, request);
            SipCall.checkObjectNull("Fail to create new response", response);

            // Send
            serverTransaction.sendResponse(response);
            logger.debug("@ Response : \n{}", response);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * @fn public void respondToCancel(final Request request, final ServerTransaction serverTransaction, final MessageFactory messageFactory)
     * @brief Cancel 요청을 처리하는 함수
     * @param request 요청(Cancel, 입력, 읽기 전용)
     * @param serverTransaction 서버 트랜잭션(입력, 읽기 전용)
     * @param messageFactory SIP 메시지 인터페이스(입력, 읽기 전용)
     * @return 반환값 없음
     */
    public void respondToCancel(final Request request, final ServerTransaction serverTransaction, final MessageFactory messageFactory) {
        SipCall.checkObjectNull(null, request, serverTransaction, messageFactory);

        CallIdHeader callIdHeader = serverTransaction.getDialog().getCallId();
        SipCall.checkObjectNull(null, callIdHeader);

        // 기존에 Invite 가 존재하면 존재하는 Invite 에 대해 487 Request Terminated
        Request oldRequest;
        if ((oldRequest = SipCall.searchRequestFromTransactionHashMap(callIdHeader, Request.INVITE)) != null) {
            ResponseManager.getInstance().respondWith487ToInviteByCancel(oldRequest, callIdHeader, messageFactory);
        }
        // 없으면 존재하지 않으면 Cancel 에 대해 481 Call/Transaction Does Not Exist
        else {
            ResponseManager.getInstance().respondWith4xx(serverTransaction, messageFactory, Response.CALL_OR_TRANSACTION_DOES_NOT_EXIST);
            return;
        }

        // Cancel 에 대해 200 OK 응답
        ResponseManager.getInstance().respondWith2xxToNonInviteReq(request, serverTransaction, messageFactory, Response.OK);
    }

    /**
     * @fn private void respondWith487ToInviteByCancel(final Request request, final CallIdHeader callIdHeader, final MessageFactory messageFactory)
     * @brief Cancel 요청에 의해 Invite 요청을 487 응답으로 처리하는 함수
     * @param request        Invite 요청(입력, 읽기 전용)
     * @param callIdHeader   현재 진행 중인 다이얼로그의 Call-ID(입력, 읽기 전용)
     * @param messageFactory SIP 메시지 인터페이스(입력, 읽기 전용)
     * @return 반환값 없음
     */
    private void respondWith487ToInviteByCancel(final Request request, final CallIdHeader callIdHeader, final MessageFactory messageFactory) {
        SipCall.checkObjectNull(null, request, callIdHeader, messageFactory);

        try {
            // 같은 Call-ID 를 가진 Transaction 을 찾는다.
            // Search Transaction which is equal to Call-ID
            Transaction transaction = SipCall.searchTransactionHashMap(callIdHeader);

            // New 487 Request Terminated Response
            Response response = messageFactory.createResponse(Response.REQUEST_TERMINATED, request);

            // Send
            ServerTransaction serverTransaction = (ServerTransaction) transaction;
            serverTransaction.sendResponse(response);
            logger.debug("@ Response : \n{}", response);

            // Terminate Transaction (Invite)
            transaction.terminate();

            // Remove Transaction
            logger.debug("Remove Transaction by Cancel, Call-ID : {}", callIdHeader);
            SipCall.removeDialogHashMap(callIdHeader);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * @fn public void respondWith4xx(final RequestEvent requestEvent, final ServerTransaction serverTransaction, final MessageFactory messageFactory, int responseType)
     * @brief 지정한 요청을 4xx 응답으로 처리하는 함수
     * @param serverTransaction 서버 트랜잭션(입력, 읽기 전용)
     * @param messageFactory    SIP 메시지 인터페이스(입력, 읽기 전용)
     * @param responseType      응답 유형(입력, 읽기 전용)
     * @return 반환값 없음
     */
    public void respondWith4xx(final ServerTransaction serverTransaction, final MessageFactory messageFactory, int responseType) {
        SipCall.checkObjectNull(null, serverTransaction, messageFactory);

        try {
            // Get Server Transaction & Request
            Request request = serverTransaction.getRequest();

            // New Response
            Response response = messageFactory.createResponse(responseType, request);

            // Send
            serverTransaction.sendResponse(response);
            logger.debug("@ Response : \n{}", response);

            // Terminate Transaction (Invite)
            serverTransaction.terminate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////
    /// @ Static Functions
    ////////////////////////////////////////////////////////////////////////////////////////

    /**
     * @fn private void respondWith1xxToInvite(final Request request, final ServerTransaction serverTransaction, final MessageFactory messageFactory, int statusCode)
     * @brief Invite 요청을 1xx 응답으로 처리하는 함수
     * @param request           요청(입력, 읽기 전용)
     * @param serverTransaction 서버 트랜잭션(입력, 읽기 전용)
     * @param messageFactory    SIP 메시지 인터페이스(입력, 읽기 전용)
     * @param statusCode        응답 코드(입력, 읽기 전용)
     * @return 반환값 없음
     */
    private void respondWith1xxToInvite(final Request request, final ServerTransaction serverTransaction, final MessageFactory messageFactory, int statusCode) {
        SipCall.checkObjectNull(null, request, serverTransaction, messageFactory);

        if(!request.getMethod().equals(Request.INVITE)) return;

        try {
            // New 1xx Response
            Response response = messageFactory.createResponse(statusCode, request);
            SipCall.checkObjectNull("Fail to create new response", response);

            // Send
            serverTransaction.sendResponse(response);
            logger.debug("@ Response : \n{}", response);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * @fn private void respondWith200ToInvite(final Request request, final ServerTransaction serverTransaction, final MessageFactory messageFactory, final HeaderFactory headerFactory, final AddressFactory addressFactory)
     * @brief Invite 요청을 200 OK 응답으로 처리하는 함수
     * @param request           요청(입력, 읽기 전용)
     * @param serverTransaction 서버 트랜잭션(입력, 읽기 전용)
     * @param messageFactory    SIP 메시지 인터페이스(입력, 읽기 전용)
     * @param headerFactory     SIP 메시지 헤더 관리 인터페이스(입력, 읽기 전용)
     * @param addressFactory    SIP 메시지 주소 관리 인터페이스(입력, 읽기 전용)
     * @return 반환값 없음
     */
    private void respondWith200ToInvite(final Request request, final ServerTransaction serverTransaction, final MessageFactory messageFactory, final HeaderFactory headerFactory, final AddressFactory addressFactory) {
        SipCall.checkObjectNull(null, request, serverTransaction, messageFactory, headerFactory, addressFactory);

        if(!request.getMethod().equals(Request.INVITE)) return;

        try {
            // Make SDP
            String sdp = SipCall.makeSdp();

            // New 200 OK Response
            Response response = messageFactory.createResponse(Response.OK, request);
            SipCall.checkObjectNull("Fail to create new response", response);

            // New Content Type Header
            ContentTypeHeader contentTypeHeader = headerFactory.createContentTypeHeader("application", "sdp");
            SipCall.checkObjectNull("Fail to create Content Type Header", contentTypeHeader);

            // Apply SDP to Content Type Header
            byte[] sdpBytes = sdp.getBytes();
            response.setContent(sdpBytes, contentTypeHeader);

            // New Contact Header
            Address address = addressFactory.createAddress("<" + request.getRequestURI().toString() + ">");
            ContactHeader contactHeader = headerFactory.createContactHeader(address);
            SipCall.checkObjectNull("Fail to create Contact Header", contactHeader);

            // Apply Contact Header to Response
            response.addHeader(contactHeader);

            // Send
            serverTransaction.sendResponse(response);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
