package com.signal.logic;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sip.ClientTransaction;
import javax.sip.Dialog;
import javax.sip.RequestEvent;
import javax.sip.SipProvider;
import javax.sip.address.Address;
import javax.sip.header.*;
import javax.sip.message.Request;
import java.util.ArrayList;
import java.util.Random;

/**
 * @class public class com.signal.logic.RequestManager
 * @brief 요청 처리 클래스
 */
public class RequestManager {
    /* 출력 레벨에 따라 지정한 데이터를 표준 출력 */
    private static final Logger logger = LoggerFactory.getLogger(RequestManager.class);
    /* Tag 번호 생성 시 최대 문자열 길이 */
    private static final int MAX_TAG_NUMBER = 256;
    /* 요청 관리 매니저(singleton) */
    private static RequestManager RequestManager = null;

    /**
     * @fn private com.signal.logic.RequestManager()
     * @brief 요청 관리 매니저 객체를 초기화하는 함수
     */
    private RequestManager() {
    }

    ////////////////////////////////////////////////////////////////////////////////////////
    /// @ Public Functions
    ////////////////////////////////////////////////////////////////////////////////////////

    /**
     * @fn public static com.signal.logic.RequestManager getInstance()
     * @brief 요청 관리 매니저의 싱글턴 인스턴스를 반환하는 함수
     * @return 요청 관리 매니저
     */
    public static RequestManager getInstance() {
        if (RequestManager == null) RequestManager = new RequestManager();
        return RequestManager;
    }

    /**
     * @fn public void requestInvite(final com.signal.logic.SipCall sipCall, final String toURI)
     * @brief Invite 요청을 보내는 함수
     * @param sipCall SIP 호 정보 관리 클래스(입력, 읽기 전용)
     * @param toURI   요청을 수신하는 URI(입력, 읽기 전용)
     * @return 반환값 없음
     */
    public void requestInvite(final SipCall sipCall, final String toURI) {
        try {
            // Contact
            Address contactAddress = sipCall.addressFactory.createAddress("sip:" + sipCall.getUserName() + "@" + sipCall.getIp() + ":" + sipCall.getPort());
            if (contactAddress == null) throw new NullPointerException("Fail to create Contact Header");
            ContactHeader contactHeader = sipCall.headerFactory.createContactHeader(contactAddress);

            // RequestURI
            Address addressTo = sipCall.addressFactory.createAddress("sip:" + toURI + ":" + 5060);
            if (addressTo == null) throw new NullPointerException("Fail to create new To Header");
            javax.sip.address.URI requestURI = addressTo.getURI();

            // Via
            ArrayList<ViaHeader> viaHeaders = new ArrayList<>();
            ViaHeader viaHeader = sipCall.headerFactory.createViaHeader(sipCall.getIp(), sipCall.getPort(), "udp", null);
            if (viaHeader == null) throw new NullPointerException("Fail to create new Via Header");
            viaHeaders.add(viaHeader);

            // Max-forwards
            MaxForwardsHeader maxForwardsHeader = sipCall.headerFactory.createMaxForwardsHeader(70);
            if (maxForwardsHeader == null) throw new NullPointerException("Fail to create new Max-Forwards Header");

            // Call-ID
            CallIdHeader callIdHeader = sipCall.getSipProvider().getNewCallId();
            if (callIdHeader == null) throw new NullPointerException("Fail to create new Call-ID Header");

            // CSeq
            CSeqHeader cSeqHeader = sipCall.headerFactory.createCSeqHeader(1L, "INVITE");
            if (cSeqHeader == null) throw new NullPointerException("Fail to create new CSeq Header");

            // From
            String tag = makeTag();
            FromHeader fromHeader = sipCall.headerFactory.createFromHeader(contactAddress, tag);
            if (fromHeader == null) throw new NullPointerException("Fail to create new From Header");

            // To
            ToHeader toHeader = sipCall.headerFactory.createToHeader(addressTo, null);
            if (toHeader == null) throw new NullPointerException("Fail to create new To Header");

            Request request = sipCall.messageFactory.createRequest(
                    requestURI, "INVITE", callIdHeader, cSeqHeader, fromHeader,
                    toHeader, viaHeaders, maxForwardsHeader);
            if (request == null) throw new NullPointerException("Fail to create new Request");

            request.addHeader(contactHeader);

            // SDP
            String sdp = SipCall.makeSdp();
            byte[] contents = sdp.getBytes();
            ContentTypeHeader contentTypeHeader = sipCall.headerFactory.createContentTypeHeader("application", "sdp");
            if (contentTypeHeader == null) throw new NullPointerException("Fail to create Content Type Header");
            request.setContent(contents, contentTypeHeader);

            // New Client Transaction
            ClientTransaction clientTransaction = sipCall.getSipProvider().getNewClientTransaction(request);
            if (clientTransaction == null) throw new NullPointerException("Fail to create Client Transaction");

            // New Dialog
            Dialog dialog = sipCall.getSipProvider().getNewDialog(clientTransaction);
            if (dialog == null) throw new NullPointerException("Fail to create Dialog");

            // Add Transaction
            logger.debug("Invite Call-ID : {}", dialog.getCallId());
            SipCall.addTransactionHashMap(dialog.getCallId(), clientTransaction);

            // Send
            clientTransaction.sendRequest();
            logger.debug("@ Request :\n{}", request);

            logger.info("(Before) Transaction Hash Map Size : {}", SipCall.getTransactionHashMap().size());
            logger.info("(Before) Dialog Hash Map Size : {}", SipCall.getDialogHashMap().size());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * @fn public void requestBye(final RequestEvent requestEvent)
     * @brief Bye 요청을 보내는 함수
     * @param requestEvent 요청 이벤트(입력, 읽기 전용)
     * @return 반환값 없음
     */
    public void requestBye(final RequestEvent requestEvent) {
        if (requestEvent == null) throw new NullPointerException("Parameter Error");

        try {
            Dialog dialog = requestEvent.getDialog();
            CallIdHeader callIdHeader = dialog.getCallId();

            // Find Dialog
            if (SipCall.findDialogHashMap(callIdHeader)) { // Call/Transaction Does Not Exist
                logger.debug("Call/Transaction Does Not Exist, fail to send Bye Request");
                return;
            }

            SipProvider provider = (SipProvider) requestEvent.getSource();

            // New Bye Request
            Request byeRequest = dialog.createRequest(Request.BYE);
            if (byeRequest == null) throw new NullPointerException("Fail to create Bye");

            // New Client Transaction
            ClientTransaction clientTransaction = provider.getNewClientTransaction(byeRequest);
            if (clientTransaction == null) throw new NullPointerException("Fail to create Client Transaction");
            dialog.sendRequest(clientTransaction);
            SipCall.addTransactionHashMap(dialog.getCallId(), clientTransaction);

            logger.debug("Bye Call-ID : {}", callIdHeader);
            logger.debug("@ Request : \n{}", byeRequest);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////
    /// @ Static Function
    ////////////////////////////////////////////////////////////////////////////////////////

    /**
     * @fn private String makeTag()
     * @brief Tag 문자열을 생성하는 함수
     * @return Tag 문자열(최대 길이:MAX_TAG_NUMBER)
     */
    private String makeTag() {
        StringBuilder tag = new StringBuilder();
        Random random = new Random();

        int tagNumber = random.nextInt(MAX_TAG_NUMBER);

        for (int i = 0; i < tagNumber; i++) {
            switch (random.nextInt(3)) {
                case 0: // a-z
                    tag.append((char) (random.nextInt(26) + 97));
                    break;
                case 1: // A-Z
                    tag.append((char) (random.nextInt(26) + 65));
                    break;
                case 2: // 0-9
                    tag.append((random.nextInt(10)));
                    break;
            }
        }

        return tag.toString();
    }
}
