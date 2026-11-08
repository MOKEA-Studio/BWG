package kr.blackwiki.smp;

import org.bukkit.configuration.file.YamlConfiguration;
import java.nio.file.*;
import java.util.*;
import java.io.IOException;

final class TransferLedger {
    enum State { PREPARED, DEBITED, COMMITTED, CANCELLED }
    record Transfer(UUID id,UUID payer,UUID receiver,long amount,State state){Transfer {Objects.requireNonNull(id);Objects.requireNonNull(payer);Objects.requireNonNull(receiver);Objects.requireNonNull(state);if(amount<1||payer.equals(receiver))throw new IllegalArgumentException("Invalid coin transfer");}}
    private final Path path;private Map<UUID,Transfer> entries=new LinkedHashMap<>();
    TransferLedger(Path path)throws Exception{this.path=path;if(!Files.exists(path))return;YamlConfiguration y=new YamlConfiguration();y.load(path.toFile());if(y.getInt("schema")!=1)throw new IOException("Unknown transfer schema");var all=y.getConfigurationSection("transfers");if(all!=null)for(String key:all.getKeys(false)){String at="transfers."+key+".";var t=new Transfer(UUID.fromString(key),UUID.fromString(Objects.requireNonNull(y.getString(at+"payer"))),UUID.fromString(Objects.requireNonNull(y.getString(at+"receiver"))),y.getLong(at+"amount"),State.valueOf(Objects.requireNonNull(y.getString(at+"state"))));entries.put(t.id,t);}}
    List<Transfer> all(){return List.copyOf(entries.values());}
    Transfer get(UUID id){return entries.get(id);}
    void put(Transfer transfer)throws IOException{Map<UUID,Transfer> next=new LinkedHashMap<>(entries);next.put(transfer.id,transfer);DataStore.atomic(path,snapshot(next));entries=next;}
    String snapshot(){return snapshot(entries);}
    private static String snapshot(Map<UUID,Transfer> entries){YamlConfiguration y=new YamlConfiguration();y.set("schema",1);for(var t:entries.values()){String at="transfers."+t.id+".";y.set(at+"payer",t.payer.toString());y.set(at+"receiver",t.receiver.toString());y.set(at+"amount",t.amount);y.set(at+"state",t.state.name());}return y.saveToString();}
    void compact()throws IOException{var done=entries.values().stream().filter(t->t.state==State.COMMITTED||t.state==State.CANCELLED).toList();if(done.size()<=2000)return;Map<UUID,Transfer> next=new LinkedHashMap<>(entries);for(int i=0;i<done.size()-2000;i++)next.remove(done.get(i).id);DataStore.atomic(path,snapshot(next));entries=next;}
}
