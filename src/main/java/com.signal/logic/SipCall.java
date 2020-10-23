package com.signal.logic;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sip.*;
import javax.sip.address.AddressFactory;
import javax.sip.header.CSeqHeader;
import javax.sip.header.CallIdHeader;
import javax.sip.header.HeaderFactory;
import javax.sip.message.MessageFactory;
import javax.sip.message.Request;
import javax.sip.message.Response;
import java.util.HashMap;
import java.util.Properties;

/**
 * @class public class com.signal.logic.SipCall implements SipListener
 * @brief SIP 호 정보 관리 클래스
 */
public class SipCall implements SipListener {
    /* 출력 레벨에 따라 지정한 데이터를 표준 출력 */
    private static final Logger logger = LoggerFactory.getLogger(SipCall.class);
    /* 트랜잭션 관리 해쉬 맵, 현재 설립된 다이얼로그에서 처리 중인 트랜잭션에 대한 정보를 관리 */
    private static HashMap<CallIdHeader, Transaction> transactionHashMap;
    /* 다이얼로그 관리 해쉬 맵, 현재 설립된 다이얼로그에 대한 정보를 관리 */
    private static HashMap<CallIdHeader, Dialog> dialogHashMap;
    /* 사용자 이름 */
    private final String userName;
    /* IP 주소 */
    private final String ip;
    /* 포트 번호 */
    private final int port;
    /* SIP 메시지 전체 정보 관리 인터페이스 */
    public SipFactory sipFactory;
    /* SIP 메시지 주소 관리 인터페이스 */
    public AddressFactory addressFactory;
    /* SIP 메시지 헤더 관리 인터페이스 */
    public HeaderFactory headerFactory;
    /* SIP 메시지 관리 인터페이스 */
    public MessageFactory messageFactory;
    /* SIP Stack 관리 인터페이스 */
    private SipProvider sipProvider;
    /* 전체적인 SIP 세션 및 트랜잭션 관리 인터페이스 */
    private SipStack sipStack;

    ////////////////////////////////////////////////////////////////////////////////////////
    /// @ Public Functions
    ////////////////////////////////////////////////////////////////////////////////////////

    /**
     * @fn public com.signal.logic.SipCall(final String userName, final String ip, int port, final String protocol)
     * @brief SIP 호 정보 관리 객체를 초기화하는 함수
     * @param userName SIP URI 에서 사용될 사용자 이름(입력, 읽기 전용)
     * @param ip       SIP URI 에서 사용될 IP 주소(입력, 읽기 전용)
     * @param port     SIP Stack 에 사용될 포트 번호(입력)
     * @param protocol SIP Stack 에 사용될 프로토콜 이름(입력, 읽기 전용)
     */
    public SipCall(final String userName, final String ip, int port, final String protocol) {
        if (userName == null || ip == null || port <= 0 || protocol == null)
            throw new NullPointerException("Parameter Error");

        this.userName = userName;
        this.ip = ip;
        this.port = port;

        sipFactory = SipFactory.getInstance();
        Properties properties = new Properties();
        properties.setProperty("javax.sip.IP_ADDRESS", "0.0.0.0");
        properties.setProperty("javax.sip.STACK_NAME", "SIG_DEMO");
        properties.setProperty("gov.nist.javax.sip.DEBUG_LOG", "debug.log");
        properties.setProperty("gov.nist.javax.sip.SERVER_LOG", "debug.log");

        // New SipStack
        try {
            sipStack = sipFactory.createSipStack(properties);
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println(e.getMessage());
            if (e.getCause() != null)
                e.getCause().printStackTrace();
            System.exit(0);
        }

        try {
            // New SipFactory
            this.headerFactory = sipFactory.createHeaderFactory();
            this.addressFactory = sipFactory.createAddressFactory();
            this.messageFactory = sipFactory.createMessageFactory();

            // New ListeningPoint & SipProvider
            ListeningPoint listeningPoint = sipStack.createListeningPoint(ip, port, protocol);
            this.sipProvider = sipStack.createSipProvider(listeningPoint);
            this.sipProvider.addSipListener(this);
            this.sipProvider.setAutomaticDialogSupportEnabled(false);

            // Start SipStack
            sipStack.start();

            // New Transaction Hash Map
            HashMap<CallIdHeader, Transaction> _transactionHashMap = new HashMap<>();
            SipCall.setTransactionHashMap(_transactionHashMap);

            // New Dialog Hash Map
            HashMap<CallIdHeader, Dialog> _dialogHashMap = new HashMap<>();
            SipCall.setDialogHashMap(_dialogHashMap);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * @fn public static HashMap<CallIdHeader, Transaction> getTransactionHashMap()
     * @brief 트랜잭션 관리 해쉬 맵을 반환하는 함수
     * @return 트랜잭션 관리 해쉬 맵
     */
    public static HashMap<CallIdHeader, Transaction> getTransactionHashMap() {
        return transactionHashMap;
    }

    /**
     * @fn public static void setTransactionHashMap(final HashMap<CallIdHeader, Transaction> transactionHashMap)
     * @brief 트랜잭션 관리 해쉬 맵을 초기화하는 함수
     * @param transactionHashMap 트랜잭션 관리 해쉬 맵(입력, 읽기 전용)
     * @return 반환값 없음
     */
    public static void setTransactionHashMap(final HashMap<CallIdHeader, Transaction> transactionHashMap) {
        SipCall.transactionHashMap = transactionHashMap;
    }

    /**
     * @fn public static void addTransactionHashMap(final CallIdHeader callIdHeader, final Transaction transaction)
     * @brief 트랜잭션 관리 해쉬 맵에 지정한 Call-ID(키)와 트랜잭션(밸류)를 추가하는 함수
     * @param callIdHeader Call-ID 헤더(입력, 읽기 전용)
     * @param transaction  트랜잭션(입력, 읽기 전용)
     * @return 반환값 없음
     */
    public static void addTransactionHashMap(final CallIdHeader callIdHeader, final Transaction transaction) {
        if (callIdHeader != null && transaction != null) transactionHashMap.put(callIdHeader, transaction);
    }

    /**
     * @fn public static void removeTransactionHashMap(final CallIdHeader callIdHeader)
     * @brief 트랜잭션 관리 해쉬 맵에서 지정한 Call-ID(키)에 해당하는 트랜잭션(밸류)를 삭제하는 함수
     * @param callIdHeader Call-ID 헤더(입력, 읽기 전용)
     * @return 반환값 없음
     */
    public static void removeTransactionHashMap(final CallIdHeader callIdHeader) {
        if (callIdHeader == null) throw new NullPointerException("Parameter Error");
        if (transactionHashMap.isEmpty()) return;
        transactionHashMap.remove(callIdHeader);
    }

    /**
     * @fn public static Transaction searchTransactionHashMap(final CallIdHeader callIdHeader)
     * @brief 트랜잭션 관리 해쉬 맵에서 지정한 Call-ID(키)에 해당하는 트랜잭션(밸류)를 검색하여 반환하는 함수
     * @param callIdHeader Call-ID 헤더(입력, 읽기 전용)
     * @return 트랜잭션
     */
    public static Transaction searchTransactionHashMap(final CallIdHeader callIdHeader) {
        if (callIdHeader == null) throw new NullPointerException("Parameter Error");
        return transactionHashMap.get(callIdHeader);
    }

    /**
     * @fn public static Request searchRequestFromTransactionHashMap(final CallIdHeader callIdHeader, final String requestType)
     * @brief 트랜잭션 관 해쉬 맵에서 지정한 Call-ID(키)에 해당하는 트랜잭션(밸류)를 검색하여 해당 트랜잭션에 속한 요청을 반환하는 함수
     * @param callIdHeader Call-ID 헤더(입력, 읽기 전용)
     * @param requestType  요청 유형(입력, 읽기 전용)
     * @return 요청
     */
    public static Request searchRequestFromTransactionHashMap(final CallIdHeader callIdHeader, final String requestType) {
        if (callIdHeader == null) throw new NullPointerException("Parameter Error");

        // 같은 Call-ID 를 가진 Transaction 을 찾는다.
        Transaction transaction = SipCall.searchTransactionHashMap(callIdHeader);
        if (transaction == null) {
            return null;
        }

        // 지정한 요청을 처리하는 Transaction 인지 확인한다.
        Request request = transaction.getRequest();
        if (!request.getMethod().equals(requestType)) {
            return null;
        }

        return request;
    }

    /**
     * @fn public static HashMap<CallIdHeader, Dialog> getDialogHashMap()
     * @brief 다이얼로그 관리 해쉬 맵을 반환하는 함수
     * @return 다이얼로그 관리 해쉬 맵
     */
    public static HashMap<CallIdHeader, Dialog> getDialogHashMap() {
        return dialogHashMap;
    }

    /**
     * @fn public static void setDialogHashMap(final HashMap<CallIdHeader, Dialog> dialogHashMap)
     * @brief 다이얼로그 관리 해쉬 맵을 초기화하는 함수
     * @param dialogHashMap 다이얼로그 관리 해쉬 맵(입력, 읽기 전용)
     * @return 반환값 없음
     */
    public static void setDialogHashMap(final HashMap<CallIdHeader, Dialog> dialogHashMap) {
        SipCall.dialogHashMap = dialogHashMap;
    }

    /**
     * @fn public static void addDialogHashMap(final CallIdHeader callIdHeader, final Dialog dialog)
     * @brief 다이얼로그 관리 해쉬 맵에 지정한 Call-ID(키)와 다이얼로그(밸류)를 추가하는 함수
     * @param callIdHeader Call-ID 헤더(입력, 읽기 전용)
     * @param dialog       다이얼로그(입력, 읽기 전용)
     * @return 반환값 없음
     */
    public static void addDialogHashMap(final CallIdHeader callIdHeader, final Dialog dialog) {
        if (callIdHeader != null && dialog != null) dialogHashMap.put(callIdHeader, dialog);
    }

    /**
     * @fn public static void removeDialogHashMap(final CallIdHeader callIdHeader)
     * @brief 다이얼로그 관리 해쉬 맵에서 지정한 Call-ID(키)에 해당하는 다이얼로그(밸류)를 삭제하는 함수
     * @param callIdHeader Call-ID 헤더(입력, 읽기 전용)
     * @return 반환값 없음
     */
    public static void removeDialogHashMap(final CallIdHeader callIdHeader) {
        if (callIdHeader == null) throw new NullPointerException("Parameter Error");
        if (dialogHashMap.isEmpty()) return;
        dialogHashMap.remove(callIdHeader);
    }

    /**
     * @fn public static boolean findDialogHashMap(final CallIdHeader callIdHeader)
     * @brief 다이얼로그 관리 해쉬 맵에서 지정한 Call-ID(키)에 해당하는 트랜잭션(밸류)를 검색하여 존재 여부를 반환하는 함수
     * @param callIdHeader Call-ID 헤더(입력, 읽기 전용)
     * @return 다이얼로그 존재 여부(boolean)
     */
    public static boolean findDialogHashMap(CallIdHeader callIdHeader) {
        if (callIdHeader == null) throw new NullPointerException("Parameter Error");
        Dialog dialog = dialogHashMap.get(callIdHeader);
        return dialog == null;
    }

    /**
     * @fn public static String makeSdp()
     * @brief SDP 메시지를 생성해주는 함수
     * @return SDP 메시지 문자열
     */
    public static String makeSdp() {
        String sdp = "";

        sdp += "v=0\r\n";
        sdp += "o=jamesj 1906 3217 IN IP4 127.0.0.1\r\n";
        sdp += "s=Talk\r\n";
        sdp += "c=IN IP4 127.0.0.1\r\n";
        sdp += "t=0 0\r\n";
        sdp += "a=rtcp-xr:rcvr-rtt=all:10000 stat-summary=loss,dup,jitt,TTL voIP-metrics\r\n";
        sdp += "m=audio 7078 RTP/AVP 96 97 101 98\r\n";
        sdp += "a=rtpmap:96 AMR/8000\r\n";
        sdp += "a=fmtp:96 octet-align=1\r\n";

        return sdp;
    }

    /**
     * @fn public static ServerTransaction getServerTransactionFromRequestEvent(final RequestEvent requestEvent)
     * @brief 요청 이벤트로부터 서버 트랜잭션을 반환하는 함수
     * 기존에 서버 트랜잭션이 존재하지 않으면 새로운 서버 트랜잭션을 생성해서 반환
     * @param requestEvent 요청 이벤트(입력, 읽기 전용)
     * @return 서버 트랜잭션
     */
    public static ServerTransaction getServerTransactionFromRequestEvent(final RequestEvent requestEvent) {
        if (requestEvent == null) throw new NullPointerException("Parameter Error");

        // 요청 이벤트에서 서버 트랜잭션을 얻어서 반환한다. 없으면 새로 생성해서 반환한다.
        ServerTransaction serverTransaction = requestEvent.getServerTransaction();
        if (serverTransaction == null) {
            try {
                // Get Request
                Request request = requestEvent.getRequest();
                if (request == null) throw new NullPointerException("Fail to get Request");

                // Get Sip Provider
                SipProvider sipProvider = (SipProvider) requestEvent.getSource();

                // New Server Transaction
                serverTransaction = sipProvider.getNewServerTransaction(request);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return serverTransaction;
    }

    /**
     * @fn public static Dialog getDialogFromRequestEvent(final RequestEvent requestEvent, final ServerTransaction serverTransaction)
     * @brief 요청 이벤트로와 서버 트랜잭션으로부터 다이얼로그를 반환하는 함수
     * 기존에 다이얼로그가 존재하지 않으면 새로운 다이얼로그를 생성해서 반환
     * @param requestEvent      요청 이벤트(입력, 읽기 전용)
     * @param serverTransaction 서버 트랜잭션(입력, 읽기 전용)
     * @return 다이얼로그
     */
    public static Dialog getDialogFromRequestEvent(final RequestEvent requestEvent, final ServerTransaction serverTransaction) {
        if (requestEvent == null || serverTransaction == null) throw new NullPointerException("Parameter Error");

        // 요청 이벤트에서 다이얼로그를 얻어서 반환한다. 없으면 새로 생성해서 반환한다.
        Dialog dialog = requestEvent.getDialog();
        if (dialog == null) {
            SipProvider sipProvider = (SipProvider) requestEvent.getSource();

            try {
                dialog = sipProvider.getNewDialog(serverTransaction);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return dialog;
    }

    /**
     * @fn public SipProvider getSipProvider()
     * @brief SipProvider 객체를 반환하는 함수
     * @return SipProvider 객체
     */
    public SipProvider getSipProvider() {
        return this.sipProvider;
    }

    /**
     * @fn public String getUserName()
     * @brief 사용자 이름을 반환하는 함수
     * @return 사용자 이름
     */
    public String getUserName() {
        return userName;
    }

    /**
     * @fn public String getIp()
     * @brief IP 주소를 반환하는 함수
     * @return IP 주소
     */
    public String getIp() {
        return ip;
    }

    /**
     * @fn public String getPort()
     * @brief 포트 번호를 반환하는 함수
     * @return 포트 번호
     */
    public int getPort() {
        return port;
    }

    ////////////////////////////////////////////////////////////////////////////////////////
    /// @ Public Override Functions
    ////////////////////////////////////////////////////////////////////////////////////////

    /**
     * @fn public void processRequest(final RequestEvent requestEvent)
     * @brief 수신된 요청을 처리하는 함수(javax sip override)
     * @param requestEvent 요청 이벤트(입력, 읽기 전용)
     * @return 반환값 없음
     */
    @Override
    public void processRequest(final RequestEvent requestEvent) {
        if (requestEvent == null) throw new NullPointerException("Parameter Error");

        // Get Request
        Request request = requestEvent.getRequest();

        // Get Server Transaction
        ServerTransaction serverTransaction = SipCall.getServerTransactionFromRequestEvent(requestEvent);
        if (serverTransaction == null) throw new NullPointerException("Fail to get Server Transaction");

        logger.debug("(Before) Transaction Hash Map Size : {}", SipCall.getTransactionHashMap().size());
        logger.debug("# Request : \n{}", request);

        // 요청 유형에 따라 처리
        switch (request.getMethod()) {
            case Request.INVITE: {
                ResponseManager.getInstance().respondToInvite(requestEvent, serverTransaction, messageFactory, addressFactory, headerFactory, sipProvider, port, this);
                break;
            }
            case Request.ACK: {
//                try {
//                    TimeUnit.SECONDS.sleep(2);
//                } catch (InterruptedException e) {
//                    e.printStackTrace();
//                }
//
//                com.signal.logic.RequestManager.getInstance().requestBye(requestEvent);
                break;
            }
            case Request.BYE: {
                ResponseManager.getInstance().respondToBye(requestEvent, messageFactory);
                break;
            }
            case Request.CANCEL: {
                ResponseManager.getInstance().respondToCancel(request, serverTransaction, messageFactory);
                break;
            }
            case Request.MESSAGE:{
                // Message 요청이고 현재 다이얼로그가 생성되어 있으면, 202 Accepted 응답으로 처리
                if(dialogHashMap.size() > 0) {
                    ResponseManager.getInstance().respondWith2xxToNonInviteReq(request, serverTransaction, messageFactory, Response.ACCEPTED);
                }
            }
            default: {
                logger.debug("Unknown Request!!");
                break;
            }
        }

        logger.debug("(After) Transaction Hash Map Size : {}", transactionHashMap.size());
    }

    /**
     * @fn public void processResponse(final ResponseEvent responseEvent)
     * @brief 수신된 응답을 처리하는 함수(javax sip override)
     * @param responseEvent 응답 이벤트(입력, 읽기 전용)
     * @return 반환값 없음
     */
    @Override
    public void processResponse(final ResponseEvent responseEvent) {
        if (responseEvent == null) throw new NullPointerException("Parameter Error");

        // Get Response
        Response response = responseEvent.getResponse();
        int responseCode = response.getStatusCode();

        // 응답 유형에 따라 처리
        switch (responseCode) {
            case 100:
            case 180:
                break;
            case 200: {
                // Get Dialog
                Dialog dialog = responseEvent.getClientTransaction().getDialog();
                try {
                    // Get Call-ID Header
                    CallIdHeader callIdHeader = dialog.getCallId();
                    SipCall.removeTransactionHashMap(callIdHeader);

                    String methodName = responseEvent.getClientTransaction().getRequest().getMethod();

                    // 200 OK 응답일 때 Method 가 Invite 이면 ACK 전송
                    if (methodName.equals(Request.INVITE)) {
                        // New ACK Request
                        Request request = dialog.createAck(((CSeqHeader) response.getHeader("CSeq")).getSeqNumber());
                        if(request == null) throw new NullPointerException("Fail to create ACK Request");

                        // Send
                        dialog.sendAck(request);

                        SipCall.addDialogHashMap(callIdHeader, dialog);
                        logger.debug("### Initial Dialog Hash Map Size : {}", dialogHashMap.size());
                        break;
                    }

                    // Method 가 Bye 이면 프로그램 종료
                    if (methodName.equals(Request.BYE)) {
                        SipCall.removeDialogHashMap(callIdHeader);
                        logger.debug("### Final Dialog Hash Map Size : {}", dialogHashMap.size());
                        System.exit(0);
                    }
                } catch (InvalidArgumentException | SipException e) {
                    e.printStackTrace();
                }
                break;
            }
            default: {
                logger.debug("Unknown code : {}", responseCode);
            }
        }
    }

    /**
     * @fn public void processTimeout(final TimeoutEvent timeoutEvent)
     * @brief 시간 초과 이벤트를 처리하는 함수(javax sip override)
     * @param timeoutEvent 시간 초과 이벤트(입력, 읽기 전용)
     * @return 반환값 없음
     */
    @Override
    public void processTimeout(final TimeoutEvent timeoutEvent) {
        if (timeoutEvent == null) throw new NullPointerException("Parameter Error");

        logger.debug("Timeout occurred!!");
        String methodName;

        if (timeoutEvent.isServerTransaction()) { // 요청을 받는 트랜잭션
            logger.debug("In the Server transaction");
            methodName = ResponseManager.getInstance().respondToTimeout(timeoutEvent, messageFactory);
        } else { // 요청을 보내는 트랜잭션
            // Get Client Transaction
            logger.debug("In the Client transaction");
            ClientTransaction clientTransaction = timeoutEvent.getClientTransaction();
            methodName = clientTransaction.getRequest().getMethod();

            // Get State Name
            String stateName = clientTransaction.getState().toString();
            logger.debug("State : {}", stateName);

            // Completed 이전 State 이면 현재 트랜잭션을 취소한다.
            if (stateName.equals("Calling") || stateName.equals("Trying") || stateName.equals("Proceeding")) {
                try {
                    clientTransaction.createCancel();
                    clientTransaction.sendRequest();
                } catch (SipException e) {
                    e.printStackTrace();
                }
            }

            SipCall.removeTransactionHashMap(clientTransaction.getDialog().getCallId());
            logger.debug("(After) Transaction Hash Map Size : {}", transactionHashMap.size());
        }

        if (methodName != null) {
            logger.debug("Method : {}", methodName);
        }

        logger.debug("Timeout : {}", timeoutEvent.getTimeout().getValue());
    }

    /**
     * @fn public void processIOException(final IOExceptionEvent ioExceptionEvent)
     * @brief IO 예외를 처리하는 함수(javax sip override)
     * @param ioExceptionEvent IO 예외 이벤트(입력, 읽기 전용)
     * @return 반환값 없음
     */
    @Override
    public void processIOException(final IOExceptionEvent ioExceptionEvent) {
        if (ioExceptionEvent == null) throw new NullPointerException("Parameter Error");
        logger.debug("Host : {}", ioExceptionEvent.getHost());
        logger.debug("Transport : {}", ioExceptionEvent.getTransport());
        logger.debug("Port : {}", ioExceptionEvent.getPort());

        // Get SipProvider
        SipProvider sipProvider = (SipProvider) ioExceptionEvent.getSource();
        if (sipProvider == null) throw new NullPointerException("Fail to get SIP Provider");

        // Remove All ListeningPoints
        try {
            ListeningPoint[] listeningPoints = sipProvider.getListeningPoints();
            for (ListeningPoint lp : listeningPoints) {
                if (lp != null) sipProvider.removeListeningPoint(lp);
            }
            sipProvider.removeSipListener(this);
        } catch (ObjectInUseException e) {
            e.printStackTrace();
        }

        // Stop SipStack
        sipProvider.getSipStack().stop();

        // 프로그램 종료
        System.exit(0);
    }

    /**
     * @fn public void processTransactionTerminated(final TransactionTerminatedEvent transactionTerminatedEvent)
     * @brief 트랜잭션이 종료되었을 때 호출되는 함수(javax sip override)
     * @param transactionTerminatedEvent 트랜잭션 종료 이벤트(입력, 읽기 전용)
     * @return 반환값 없음
     */
    @Override
    public void processTransactionTerminated(final TransactionTerminatedEvent transactionTerminatedEvent) {
        if (transactionTerminatedEvent == null) throw new NullPointerException("Parameter Error");

        Transaction transaction;
        String transactionType;
        String branchID;
        String methodName;
        TransactionState transactionState;

        if (transactionTerminatedEvent.isServerTransaction()) {
            transaction = transactionTerminatedEvent.getServerTransaction();
            if (transaction == null) throw new NullPointerException("Fail to get Server Transaction");
            transactionType = "Server";
        } else {
            transaction = transactionTerminatedEvent.getClientTransaction();
            if (transaction == null) throw new NullPointerException("Fail to get Client Transaction");
            transactionType = "Client";
        }

        branchID = transaction.getBranchId();
        transactionState = transaction.getState();
        methodName = transaction.getRequest().getMethod();
        logger.debug("{} Transaction (Method:{}, BranchID:{}, State:{}) is terminated.", transactionType, methodName, branchID, transactionState);
    }

    /**
     * @fn public void processDialogTerminated(final DialogTerminatedEvent dialogTerminatedEvent)
     * @brief 다이얼로그가 종료되었을 때 호출되는 함수(javax sip override)
     * @param dialogTerminatedEvent 다이얼로그 종료 이벤트(입력, 읽기 전용)
     * @return 반환값 없음
     */
    @Override
    public void processDialogTerminated(final DialogTerminatedEvent dialogTerminatedEvent) {
        if (dialogTerminatedEvent == null) throw new NullPointerException("Parameter Error");

        Dialog dialog = dialogTerminatedEvent.getDialog();
        if (dialog == null) throw new NullPointerException("Fail to get Dialog");

        DialogState dialogState = dialog.getState();
        CallIdHeader callIdHeader = dialog.getCallId();
        if (callIdHeader == null) throw new NullPointerException("Fail to get Call-ID");

        String callId = callIdHeader.getCallId();
        String localTag = dialog.getLocalTag();
        String remoteTag = dialog.getRemoteTag();

        logger.debug("Dialog (CallID:{}, State:{}, LocalTag:{}, RemoteTag:{}) is terminated.", callId, dialogState, localTag, remoteTag);
    }
}
