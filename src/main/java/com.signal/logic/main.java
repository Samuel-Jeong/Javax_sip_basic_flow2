package com.signal.logic;

/**
 * @class public class main
 * @brief SIP 기본 호 시험을 실행하는 클래스
 */
public class main {
    /**
     * @fn public static void main(String[] args) throws SipException
     * @brief SIP 기본 호 시험을 진행하는 함수
     * @param args 매개변수(입력)
     * @return 반환값 없음
     */
    public static void main(String[] args) {
        SipCall sipCall = new SipCall("myself", "127.0.0.1", 5070, "udp");

        RequestManager.getInstance().requestInvite(sipCall, "jamesj@127.0.0.1");
    }
}
