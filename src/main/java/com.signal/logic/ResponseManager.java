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
 * @class public class com.signal.logic.ResponseManager
 * @brief 응답 처리 클래스
 */
public class ResponseManager {
    /* 출력 레벨에 따라 지정한 데이터를 표준 출력 */
    private static final Logger logger = LoggerFactory.getLogger(ResponseManager.class);
    /* 응답 관리 매니저(singleton) */
    public static ResponseManager responseManager = null;

    /**
     * @fn private com.signal.logic.ResponseManager()
     * @brief 응답 관리 매니저 객체를 초기화하는 함수
     */
    private ResponseManager() {
    }

    ////////////////////////////////////////////////////////////////////////////////////////
    /// @ Public Functions
    ////////////////////////////////////////////////////////////////////////////////////////

    /**
     * @fn public static com.signal.logic.ResponseManager getInstance()
     * @brief 응답 관리 매니저의 인스턴스를 반환하는 함수
     * @return 응답 관리 매니저
     */
    public static ResponseManager getInstance() {
        if (responseManager == null) responseManager = new ResponseManager();
        return responseManager;
    }

    /**
     * @fn public void respondToInvite(final RequestEvent requestEvent, final ServerTransaction serverTransaction, final MessageFactory messageFactory, final AddressFactory addressFactory, final HeaderFactory headerFactory, final SipProvider SipProvider, int port, final com.signal.logic.SipCall sipCall)
     * @brief Invite 요청에 응답하는 함수
     * @param requestEvent      요청 이벤트(입력, 읽기 전용)
     * @param serverTransaction 서버 트랜잭션(입력, 읽기 전용)
     * @param messageFactory    SIP 메시지 인터페이스(입력, 읽기 전용)
     * @param addressFactory    SIP 메시지 주소 인터페이스(입력, 읽기 전용)
     * @param headerFactory     SIP 메시지 헤더 인터페이스(입력, 읽기 전용)
     * @param SipProvider       SIP Stack 관리 인터페이스(입력, 읽기 전용)
     * @param port              포트 번호(입력)
     * @param sipCall           SIP 호 관리 객체(입력, 읽기 전용)
     * @return 반환값 없음
     */
    public void respondToInvite(final RequestEvent requestEvent, final ServerTransaction serverTransaction, final MessageFactory messageFactory, final AddressFactory addressFactory, final HeaderFactory headerFactory, final SipProvider SipProvider, int port, final SipCall sipCall) {
        if (requestEvent == null || messageFactory == null || addressFactory == null || headerFactory == null || SipProvider == null || sipCall == null || port <= 0)
            throw new NullPointerException("Parameter Error");

        Request request = requestEvent.getRequest();
        try {
            logger.debug("@ Request :\n{}", request);

            // New Dialog & Transaction
            Dialog dialog = sipCall.getSipProvider().getNewDialog(serverTransaction);
            if (dialog == null) throw new NullPointerException("Fail to create Dialog");

            CallIdHeader callIdHeader = dialog.getCallId();
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
            logger.debug("### Initial Dialog Hash Map Size : {}", SipCall.getDialogHashMap().size());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * @fn public void respondToBye(final RequestEvent requestEvent, final MessageFactory messageFactory)
     * @brief Bye 요청에 응답하는 함수
     * @param requestEvent   요청 이벤트(입력, 읽기 전용)
     * @param messageFactory SIP 메시지 인터페이스(입력, 읽기 전용)
     * @return 반환값 없음
     */
    public void respondToBye(final RequestEvent requestEvent, final MessageFactory messageFactory) {
        if (requestEvent == null || messageFactory == null) throw new NullPointerException("Parameter Error");

        try {
            CallIdHeader callIdHeader = requestEvent.getDialog().getCallId();
            logger.debug("Bye Call-ID : {}", callIdHeader);

            // Get Request
            Request request = requestEvent.getRequest();
            Response response;

            // Find Dialog & New Response
            if (SipCall.findDialogHashMap(callIdHeader)) { // 481 Call/Transaction Does Not Exist
                response = messageFactory.createResponse(Response.CALL_OR_TRANSACTION_DOES_NOT_EXIST, request);
                logger.debug("Call/Transaction Does Not Exist");
            } else { // 200 OK
                response = messageFactory.createResponse(Response.OK, request);
            }
            if (response == null) throw new NullPointerException("Fail to create new response");

            // Get Server Transaction
            ServerTransaction serverTransaction = SipCall.getServerTransactionFromRequestEvent(requestEvent);

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
        if (timeoutEvent == null || messageFactory == null) throw new NullPointerException("Parameter Error");

        String methodName = null;

        try {
            // Get Server Transaction
            ServerTransaction serverTransaction = timeoutEvent.getServerTransaction();
            if (serverTransaction == null) throw new NullPointerException("Fail to get Server Transaction");

            // Get Request & Method Name
            Request request = serverTransaction.getRequest();
            methodName = serverTransaction.getRequest().getMethod();
            logger.debug("@ Request : \n{}", request);

            // New Timeout Response
            Response response = messageFactory.createResponse(Response.REQUEST_TIMEOUT, request);
            if (response == null) throw new NullPointerException("Fail to create new response");

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
        if (request == null || serverTransaction == null || messageFactory == null) throw new NullPointerException("Parameter Error");

        try {
            // Get Request
            logger.debug("@ Request : \n{}", request);

            // New Response
            Response response = messageFactory.createResponse(responseType, request);
            if (response == null) throw new NullPointerException("Fail to create new response");

            // Send
            serverTransaction.sendResponse(response);
            logger.debug("@ Response : \n{}", response);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * @fn public void respondWith487ToInviteByCancel(final Request request, final CallIdHeader callIdHeader, final MessageFactory messageFactory)
     * @brief Cancel 요청에 의해 Invite 요청을 487 응답으로 처리하는 함수
     * @param request        Invite 요청(입력, 읽기 전용)
     * @param callIdHeader   현재 진행 중인 다이얼로그의 Call-ID(입력, 읽기 전용)
     * @param messageFactory SIP 메시지 인터페이스(입력, 읽기 전용)
     * @return 반환값 없음
     */
    public void respondWith487ToInviteByCancel(final Request request, final CallIdHeader callIdHeader, final MessageFactory messageFactory) {
        if (request == null || callIdHeader == null || messageFactory == null)
            throw new NullPointerException("Parameter Error");

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
        if (serverTransaction == null || messageFactory == null)
            throw new NullPointerException("Parameter Error");

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
        if (request == null || serverTransaction == null || messageFactory == null)
            throw new NullPointerException("Parameter Error");

        if(!request.getMethod().equals(Request.INVITE)) return;

        try {
            // New 1xx Response
            logger.debug("@ Request : \n{}", request);
            Response response = messageFactory.createResponse(statusCode, request);
            if (response == null) throw new NullPointerException("Fail to create new response");

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
        if (request == null || serverTransaction == null || messageFactory == null ||
                headerFactory == null || addressFactory == null) throw new NullPointerException("Parameter Error");

        if(!request.getMethod().equals(Request.INVITE)) return;

        try {
            // Make SDP
            String sdp = SipCall.makeSdp();

            // New 200 OK Response
            Response response = messageFactory.createResponse(Response.OK, request);
            if (response == null) throw new NullPointerException("Fail to create new response");

            // New Content Type Header
            ContentTypeHeader contentTypeHeader = headerFactory.createContentTypeHeader("application", "sdp");
            if (contentTypeHeader == null) throw new NullPointerException("Fail to create Content Type Header");

            // Apply SDP to Content Type Header
            byte[] sdpBytes = sdp.getBytes();
            response.setContent(sdpBytes, contentTypeHeader);

            // New Contact Header
            Address address = addressFactory.createAddress("<" + request.getRequestURI().toString() + ">");
            ContactHeader contactHeader = headerFactory.createContactHeader(address);
            if (contactHeader == null) throw new NullPointerException("Fail to create Contact Header");

            // Apply Contact Header to Response
            response.addHeader(contactHeader);

            // Send
            serverTransaction.sendResponse(response);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
