package kr.blackwiki.smp;

import org.bukkit.entity.Player;

/** Only positive, single-account gameplay rewards. Cross-player transfers use a journal. */
final class RewardService {
    private final BlackWikiSMP p;
    RewardService(BlackWikiSMP p){this.p=p;}
    void credit(Player a,long amount){if(amount<0)throw new IllegalArgumentException("Negative reward");if(!p.vaults.healthy(a))return;var u=p.data.user(a);u.coins=CoinClock.add(u.coins,amount);p.vaults.syncCurrency(a,u);p.data.changed();if(!p.backups.running())p.vaults.saveAccount(a);}
}
