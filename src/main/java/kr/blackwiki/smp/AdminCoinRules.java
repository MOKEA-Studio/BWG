package kr.blackwiki.smp;

final class AdminCoinRules {
    private AdminCoinRules(){}
    static long amount(String input){if(input==null||!input.matches("[0-9]{1,19}"))throw new IllegalArgumentException("수량은 1 이상의 정수로 입력하세요.");try{long n=Long.parseLong(input);if(n<=0)throw new NumberFormatException();return n;}catch(NumberFormatException ex){throw new IllegalArgumentException("수량은 1~"+Long.MAX_VALUE+" 사이의 정수입니다.");}}
    static long balance(long before,long amount,boolean debit){if(before<0||amount<=0)throw new IllegalArgumentException("잘못된 코인 수량입니다.");if(debit){if(before<amount)throw new IllegalArgumentException("대상 플레이어의 코인이 부족합니다. 보유: "+before);return before-amount;}try{return Math.addExact(before,amount);}catch(ArithmeticException ex){throw new IllegalArgumentException("대상 플레이어의 코인 한도를 초과합니다.");}}
}
