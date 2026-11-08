package kr.blackwiki.smp;

import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

final class TitleService
implements Listener {
    private final BlackWikiSMP p;
    private final NamespacedKey ownedKey;
    private final NamespacedKey selectedKey;
    private final NamespacedKey killsKey;
    private final NamespacedKey rewardsKey;
    private final Map<UUID, State> states = new HashMap<UUID, State>();
    private final Map<UUID, Component> prefixes = new ConcurrentHashMap<UUID, Component>();
    private final Map<UUID, Quote> quotes = new HashMap<UUID, Quote>();
    private final Set<UUID> blocked = new HashSet<UUID>();
    private final Set<UUID> saving = new HashSet<UUID>();

    TitleService(BlackWikiSMP p) {
        this.p = p;
        this.ownedKey = new NamespacedKey((Plugin)p, "titles-owned");
        this.selectedKey = new NamespacedKey((Plugin)p, "title-selected");
        this.killsKey = new NamespacedKey((Plugin)p, "title-max-kills");
        this.rewardsKey = new NamespacedKey((Plugin)p, "title-kill-rewards");
    }

    private Set<String> parse(String text) {
        if (text.length() > 1000) {
            throw new IllegalArgumentException("Invalid title metadata");
        }
        return text.isBlank() ? new LinkedHashSet<String>() : new LinkedHashSet<String>(Arrays.asList(text.split("\n")));
    }

    private State state(Player a) {
        if (this.blocked.contains(a.getUniqueId())) {
            return null;
        }
        State cached = this.states.get(a.getUniqueId());
        if (cached != null) {
            return cached;
        }
        try {
            TitleCatalogue.Title title;
            PersistentDataContainer d = a.getPersistentDataContainer();
            Set<String> owned = this.parse((String)d.getOrDefault(this.ownedKey, PersistentDataType.STRING, "sprout"));
            Set<String> rewards = this.parse((String)d.getOrDefault(this.rewardsKey, PersistentDataType.STRING, ""));
            owned.add("sprout");
            for (String id : owned) {
                if (TitleCatalogue.find(id).purchase()) continue;
                throw new IllegalArgumentException("Invalid paid title receipt");
            }
            for (String id : rewards) {
                if (TitleCatalogue.find(id).reward() > 0L) continue;
                throw new IllegalArgumentException("Invalid reward receipt");
            }
            long kills = (Long)d.getOrDefault(this.killsKey, PersistentDataType.LONG, 0L);
            if (kills < 0L) {
                throw new IllegalArgumentException("Invalid title kills");
            }
            String selected = (String)d.getOrDefault(this.selectedKey, PersistentDataType.STRING, "");
            if (!selected.isEmpty() && ((title = TitleCatalogue.find(selected)).purchase() ? !owned.contains(title.id()) : kills < title.kills())) {
                throw new IllegalArgumentException("Locked title selected");
            }
            State info = new State(owned, selected, kills, rewards);
            this.states.put(a.getUniqueId(), info);
            this.cache(a, info);
            return info;
        }
        catch (Exception ex) {
            this.blocked.add(a.getUniqueId());
            this.p.getLogger().log(Level.SEVERE, "Title metadata invalid; original retained: " + String.valueOf(a.getUniqueId()), ex);
            this.p.tell((CommandSender)a, "칭호 데이터 확인이 필요합니다. 원본을 유지하고 변경을 잠갔습니다.");
            return null;
        }
    }

    private void write(Player a, State s) {
        PersistentDataContainer d = a.getPersistentDataContainer();
        d.set(this.ownedKey, PersistentDataType.STRING, String.join((CharSequence)"\n", new TreeSet<String>(s.owned)));
        d.set(this.selectedKey, PersistentDataType.STRING, s.selected);
        d.set(this.killsKey, PersistentDataType.LONG, s.kills);
        d.set(this.rewardsKey, PersistentDataType.STRING, String.join((CharSequence)"\n", new TreeSet<String>(s.rewards)));
    }

    private void cache(Player a, State info) {
        this.prefixes.put(a.getUniqueId(), (Component)(info.selected.isEmpty() ? Component.empty() : Component.text((String)(TitleCatalogue.find(info.selected).label() + " "), (TextColor)TitleCatalogue.find(info.selected).color())));
    }

    Component prefix(Player a) {
        return this.prefixes.getOrDefault(a.getUniqueId(), (Component)Component.empty());
    }

    Component tab(Player a) {
        DataStore.Team team = this.p.teams.of(a);
        return this.prefix(a).append((Component)Component.text((String)((String)(team == null ? "" : "[" + team.name + "] ") + this.p.bedrock.visibleName(a)), (TextColor)(this.p.safe(a.getLocation()) ? NamedTextColor.GREEN : NamedTextColor.WHITE)));
    }

    private void refresh(Player a) {
        a.playerListName(this.tab(a));
    }

    private boolean available(Player a) {
        if (!a.isOnline() || a.isDead() || this.p.queue.waiting(a)) {
            this.p.tell((CommandSender)a, "게임에 입장한 살아 있는 상태에서 이용하세요.");
            return false;
        }
        if (this.p.backups.running() || this.saving.contains(a.getUniqueId())) {
            this.p.tell((CommandSender)a, "저장이 끝난 뒤 칭호를 변경하세요.");
            return false;
        }
        return this.p.vaults.healthy(a) && this.state(a) != null;
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private boolean commit(Player a, State next, long balance) {
        State before = this.state(a);
        if (before == null || !this.saving.add(a.getUniqueId())) {
            return false;
        }
        DataStore.User user = this.p.data.user(a);
        long old = user.coins;
        try {
            this.write(a, next);
            user.coins = balance;
            this.p.vaults.saveAccount(a);
            this.states.put(a.getUniqueId(), next);
            this.cache(a, next);
            this.refresh(a);
            boolean bl = true;
            return bl;
        }
        catch (Exception ex) {
            user.coins = old;
            this.write(a, before);
            this.p.vaults.syncCurrency(a, user);
            this.blocked.add(a.getUniqueId());
            this.p.tell((CommandSender)a, "칭호 저장 실패로 변경을 취소했습니다. 관리자에게 문의하세요.");
            this.p.getLogger().log(Level.SEVERE, "Title transaction failed: " + String.valueOf(a.getUniqueId()), ex);
            boolean bl = false;
            return bl;
        }
        finally {
            this.saving.remove(a.getUniqueId());
        }
    }

    void sync(Player a) {
        if (!a.isOnline() || a.isDead() || this.p.backups.running() || this.saving.contains(a.getUniqueId()) || this.blocked.contains(a.getUniqueId()) || !this.p.vaults.healthy(a)) {
            return;
        }
        State old = this.state(a);
        if (old == null) {
            return;
        }
        DataStore.User user = this.p.data.user(a);
        long kills = Math.max(old.kills, user.kills);
        if (user.kills < kills) {
            user.kills = kills;
            this.p.data.changed();
        }
        LinkedHashSet<String> rewards = new LinkedHashSet<String>(old.rewards);
        long balance = user.coins;
        long earned = 0L;
        for (String id : TitleCatalogue.due(kills, old.rewards)) {
            long reward = TitleCatalogue.find(id).reward();
            if (balance > Long.MAX_VALUE - reward) continue;
            balance += reward;
            earned += reward;
            rewards.add(id);
        }
        State next = new State(old.owned, old.selected, kills, rewards);
        if (!(next.equals(old) && a.getPersistentDataContainer().has(this.killsKey, PersistentDataType.LONG) || !this.commit(a, next, balance))) {
            if (earned > 0L) {
                this.p.tell((CommandSender)a, "킬 칭호 보상 +" + VaultService.money(earned) + "코인 · 각 보상은 1회입니다.");
            }
            if (kills > old.kills && TitleCatalogue.KILLS.stream().anyMatch(t -> old.kills < t.kills() && kills >= t.kills())) {
                this.p.tell((CommandSender)a, "새 킬 칭호가 해금되었습니다. /칭호 킬");
            }
        }
    }

    boolean equip(Player a, String id) {
        if (!this.available(a)) {
            return false;
        }
        this.sync(a);
        State info = this.state(a);
        if (info == null) {
            return false;
        }
        if (!id.isEmpty()) {
            TitleCatalogue.Title t = TitleCatalogue.find(id);
            if (t.purchase() ? !info.owned.contains(t.id()) : info.kills < t.kills()) {
                this.p.tell((CommandSender)a, "아직 해금하지 않은 칭호입니다.");
                return false;
            }
            id = t.id();
        }
        if (!this.commit(a, new State(info.owned, id, info.kills, info.rewards), this.p.data.user((Player)a).coins)) {
            return false;
        }
        this.p.tell((CommandSender)a, (String)(id.isEmpty() ? "칭호를 해제했습니다." : TitleCatalogue.find(id).label() + " 칭호를 장착했습니다."));
        return true;
    }

    void quote(Player a, String id) {
        if (!this.available(a)) {
            return;
        }
        TitleCatalogue.Title title = TitleCatalogue.find(id);
        State info = this.state(a);
        if (!title.purchase() || info.owned.contains(title.id())) {
            this.equip(a, title.id());
            return;
        }
        if (!this.p.safe(a.getLocation())) {
            this.p.tell((CommandSender)a, "새 칭호는 스폰에서 구매하세요. 이미 가진 칭호는 어디서나 장착할 수 있습니다.");
            return;
        }
        Quote q = new Quote(UUID.randomUUID(), title.id(), System.currentTimeMillis() + 30000L);
        this.quotes.put(a.getUniqueId(), q);
        this.p.gui.confirm(a, title.label() + " 구매·장착 " + VaultService.money(title.price()) + "코인", b -> {
            this.purchase((Player)b, q.token);
            this.menu((Player)b, 0);
        }, b -> {
            this.quotes.remove(b.getUniqueId());
            this.menu((Player)b, 0);
        });
    }

    boolean purchase(Player a, UUID token) {
        Quote q = this.quotes.get(a.getUniqueId());
        if (q == null || !q.token.equals(token) || q.expires < System.currentTimeMillis()) {
            return false;
        }
        this.quotes.remove(a.getUniqueId());
        if (!this.available(a) || !this.p.safe(a.getLocation())) {
            this.p.tell((CommandSender)a, "칭호 구매는 스폰에서만 가능합니다.");
            return false;
        }
        TitleCatalogue.Title title = TitleCatalogue.find(q.title);
        State info = this.state(a);
        if (info.owned.contains(title.id())) {
            return this.equip(a, title.id());
        }
        long coins = this.p.data.user((Player)a).coins;
        if (coins < title.price()) {
            this.p.tell((CommandSender)a, "코인이 부족합니다. 필요 " + VaultService.money(title.price()));
            return false;
        }
        LinkedHashSet<String> next = new LinkedHashSet<String>(info.owned);
        next.add(title.id());
        return this.commit(a, new State(next, title.id(), info.kills, info.rewards), coins - title.price());
    }

    void command(Player a, String[] args) {
        if (args.length == 0) {
            this.menu(a, 0);
            return;
        }
        switch (args[0]) {
            case "킬": {
                this.menu(a, 1);
                break;
            }
            case "해제": {
                this.equip(a, "");
                break;
            }
            case "구매": {
                if (args.length != 2) {
                    throw new IllegalArgumentException("/칭호 구매 칭호ID");
                }
                this.quote(a, args[1]);
                break;
            }
            case "장착": {
                if (args.length != 2) {
                    throw new IllegalArgumentException("/칭호 장착 칭호ID");
                }
                this.equip(a, args[1]);
                break;
            }
            default: {
                this.menu(a, 0);
            }
        }
    }

    void menu(Player a, int tab) {
        if (!this.available(a)) {
            return;
        }
        this.sync(a);
        State info = this.state(a);
        if (info == null) {
            return;
        }
        Gui.Menu m = this.p.gui.make(a, "칭호 · " + (tab == 1 ? "킬 달성 6종" : "무료·구매 7종"));
        this.p.gui.button(m, 0, Material.GOLD_INGOT, "구매 칭호 7종", b -> this.menu((Player)b, 0), new String[0]);
        this.p.gui.button(m, 1, Material.IRON_SWORD, "킬 칭호 6종", b -> this.menu((Player)b, 1), new String[0]);
        this.p.gui.button(m, 2, Material.BARRIER, "칭호 해제", b -> {
            this.equip((Player)b, "");
            this.menu((Player)b, tab);
        }, new String[0]);
        m.inventory.setItem(4, Gui.item(Material.NAME_TAG, "현재: " + (info.selected.isEmpty() ? "없음" : TitleCatalogue.find(info.selected).label()), "현재 누적 킬 " + VaultService.money(info.kills), "채팅·TAB 표시 · 영구 보유"));
        List<TitleCatalogue.Title> list = tab == 1 ? TitleCatalogue.KILLS : TitleCatalogue.PAID;
        for (int i = 0; i < list.size(); ++i) {
            TitleCatalogue.Title t = list.get(i);
            boolean owned = t.purchase() ? info.owned.contains(t.id()) : info.kills >= t.kills();
            this.p.gui.button(m, 19 + i, owned ? Material.NAME_TAG : Material.PAPER, t.label(), b -> {
                if (owned) {
                    this.equip((Player)b, t.id());
                    this.menu((Player)b, tab);
                } else if (t.purchase()) {
                    this.quote((Player)b, t.id());
                } else {
                    this.p.tell((CommandSender)b, t.kills() + "킬 달성이 필요합니다.");
                }
            }, new String[]{owned ? "해금됨 · 클릭하여 장착" : (t.purchase() ? "구매 " + VaultService.money(t.price()) + "코인" : t.kills() + "킬 필요"), t.reward() > 0L ? "보상 " + VaultService.money(t.reward()) + "코인 · 1회 자동 지급" : "", "ID: " + t.id()});
        }
        this.p.gui.show(a, m);
    }

    @EventHandler(priority=EventPriority.MONITOR)
    public void join(PlayerJoinEvent e) {
        this.sync(e.getPlayer());
        this.refresh(e.getPlayer());
    }

    @EventHandler(priority=EventPriority.MONITOR)
    public void quit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        this.states.remove(id);
        this.prefixes.remove(id);
        this.quotes.remove(id);
        this.blocked.remove(id);
        this.saving.remove(id);
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void chat(AsyncChatEvent e) {
        Component prefix = this.prefix(e.getPlayer());
        if (prefix.equals(Component.empty())) {
            return;
        }
        ChatRenderer original = e.renderer();
        e.renderer((source, name, message, viewer) -> original.render(source, prefix.append(name), message, viewer));
    }

    String status() {
        return "구매 7종 · 킬 6종 · 500/1000킬 보상 각 1회 · 잠금 " + this.blocked.size() + "명";
    }

    record State(Set<String> owned, String selected, long kills, Set<String> rewards) {
        State {
            owned = Set.copyOf(owned);
            rewards = Set.copyOf(rewards);
        }
    }

    private record Quote(UUID token, String title, long expires) {
    }
}

