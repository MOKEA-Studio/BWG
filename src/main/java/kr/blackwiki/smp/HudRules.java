package kr.blackwiki.smp;

import java.util.Locale;

final class HudRules {
    private HudRules(){}
    static String compose(long coins,String status,String notice){
        return compose(coins,status,notice,"");
    }
    static String compose(long coins,String status,String notice,String weapon){
        if(coins<0)throw new IllegalArgumentException("Negative coin balance");
        String text="코인: "+String.format(Locale.ROOT,"%,d",coins);
        if(weapon!=null&&!weapon.isBlank())text+=" | "+weapon;
        if(status!=null&&!status.isBlank())text+=" | "+status;
        if(notice!=null&&!notice.isBlank())text+=" | "+notice;
        return text;
    }
}
