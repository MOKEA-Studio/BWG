package kr.blackwiki.smp;

import org.bukkit.configuration.file.YamlConfiguration;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Durable escrow journal. Publish memory only after the replacement file is durable. */
final class MarketLedger {
    enum State { DRAFT, ACTIVE, RESERVED, SOLD, PAID, RETURNING, CANCELLED }
    record Listing(UUID id,UUID seller,UUID buyer,long price,String item,State state,long created){
        Listing {Objects.requireNonNull(id);Objects.requireNonNull(seller);Objects.requireNonNull(item);Objects.requireNonNull(state);if(price<1||item.isEmpty()||created<0)throw new IllegalArgumentException("Invalid market listing");if((state==State.RESERVED||state==State.SOLD||state==State.PAID)&&buyer==null)throw new IllegalArgumentException("Missing buyer");if(seller.equals(buyer))throw new IllegalArgumentException("Self purchase");}
        Listing transition(State next,UUID customer){
            boolean valid=switch(state){case DRAFT->next==State.ACTIVE||next==State.CANCELLED;case ACTIVE->next==State.RESERVED||next==State.RETURNING;case RESERVED->next==State.ACTIVE||next==State.SOLD;case SOLD->next==State.PAID;case RETURNING->next==State.ACTIVE||next==State.CANCELLED;case PAID,CANCELLED->false;};
            if(!valid)throw new IllegalStateException("Invalid market transition: "+state+" -> "+next);
            return new Listing(id,seller,next==State.ACTIVE||next==State.CANCELLED?null:customer,price,item,next,created);
        }
        boolean finished(){return state==State.PAID||state==State.CANCELLED;}
    }
    private final Path path;
    private Map<UUID,Listing> listings=new LinkedHashMap<>();
    MarketLedger(Path path)throws Exception{this.path=path;if(!Files.exists(path))return;YamlConfiguration y=new YamlConfiguration();y.load(path.toFile());if(y.getInt("schema")!=1)throw new IOException("Unknown market schema");var section=y.getConfigurationSection("listings");if(section!=null)for(String key:section.getKeys(false)){String at="listings."+key+".";String buyer=y.getString(at+"buyer");Listing l=new Listing(UUID.fromString(key),UUID.fromString(Objects.requireNonNull(y.getString(at+"seller"))),buyer==null?null:UUID.fromString(buyer),y.getLong(at+"price"),Objects.requireNonNull(y.getString(at+"item")),State.valueOf(Objects.requireNonNull(y.getString(at+"state"))),y.getLong(at+"created"));listings.put(l.id,l);}}
    Collection<Listing> all(){return List.copyOf(listings.values());}
    Listing get(UUID id){return listings.get(id);}
    void put(Listing listing)throws IOException{Map<UUID,Listing> next=new LinkedHashMap<>(listings);next.put(listing.id,listing);write(next);listings=next;}
    String snapshot(){return yaml(listings);}
    private static String yaml(Map<UUID,Listing> entries){YamlConfiguration y=new YamlConfiguration();y.set("schema",1);for(Listing l:entries.values()){String at="listings."+l.id+".";y.set(at+"seller",l.seller.toString());y.set(at+"buyer",l.buyer==null?null:l.buyer.toString());y.set(at+"price",l.price);y.set(at+"item",l.item);y.set(at+"state",l.state.name());y.set(at+"created",l.created);}return y.saveToString();}
    private void write(Map<UUID,Listing> next)throws IOException{DataStore.atomic(path,yaml(next));}
    void compact(int history)throws IOException{List<Listing> finished=listings.values().stream().filter(Listing::finished).toList();if(finished.size()<=history)return;Map<UUID,Listing> next=new LinkedHashMap<>(listings);for(int i=0;i<finished.size()-history;i++)next.remove(finished.get(i).id);write(next);listings=next;}
}
