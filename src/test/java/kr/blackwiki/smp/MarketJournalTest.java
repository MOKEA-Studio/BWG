package kr.blackwiki.smp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MarketJournalTest {
    @TempDir Path temp;
    @Test void escrowReservationAndPayoutSurviveReload()throws Exception{
        Path file=temp.resolve("market.yml");MarketLedger ledger=new MarketLedger(file);UUID seller=UUID.randomUUID(),buyer=UUID.randomUUID();var listing=new MarketLedger.Listing(UUID.randomUUID(),seller,null,12345,"encoded-item",MarketLedger.State.DRAFT,100);
        ledger.put(listing);assertEquals(MarketLedger.State.DRAFT,new MarketLedger(file).get(listing.id()).state());listing=listing.transition(MarketLedger.State.ACTIVE,null);ledger.put(listing);listing=listing.transition(MarketLedger.State.RESERVED,buyer);ledger.put(listing);var restored=new MarketLedger(file).get(listing.id());assertEquals(buyer,restored.buyer());assertEquals(12345,restored.price());assertEquals("encoded-item",restored.item());
        listing=restored.transition(MarketLedger.State.SOLD,buyer);ledger.put(listing);assertThrows(IllegalStateException.class,()->restored.transition(MarketLedger.State.RESERVED,buyer));listing=listing.transition(MarketLedger.State.PAID,buyer);ledger.put(listing);assertTrue(new MarketLedger(file).get(listing.id()).finished());
    }
    @Test void invalidTransitionsAndSelfPurchaseRejected(){UUID seller=UUID.randomUUID();var l=new MarketLedger.Listing(UUID.randomUUID(),seller,null,1,"item",MarketLedger.State.DRAFT,0);assertThrows(IllegalStateException.class,()->l.transition(MarketLedger.State.SOLD,UUID.randomUUID()));var active=l.transition(MarketLedger.State.ACTIVE,null);assertThrows(IllegalArgumentException.class,()->active.transition(MarketLedger.State.RESERVED,seller));assertThrows(IllegalArgumentException.class,()->active.transition(MarketLedger.State.RESERVED,null));assertThrows(IllegalArgumentException.class,()->new MarketLedger.Listing(UUID.randomUUID(),seller,null,0,"item",MarketLedger.State.ACTIVE,0));}
    @Test void reservationCanBeReleasedOnlyBeforeDelivery(){UUID seller=UUID.randomUUID(),buyer=UUID.randomUUID();var a=new MarketLedger.Listing(UUID.randomUUID(),seller,buyer,5,"item",MarketLedger.State.RESERVED,0);assertNull(a.transition(MarketLedger.State.ACTIVE,null).buyer());assertThrows(IllegalStateException.class,()->a.transition(MarketLedger.State.SOLD,buyer).transition(MarketLedger.State.ACTIVE,null));}
    @Test void compactionPreservesUnfinishedOrders()throws Exception{MarketLedger ledger=new MarketLedger(temp.resolve("market.yml"));UUID seller=UUID.randomUUID();var active=new MarketLedger.Listing(UUID.randomUUID(),seller,null,5,"item",MarketLedger.State.ACTIVE,0);ledger.put(active);for(int i=0;i<4;i++)ledger.put(new MarketLedger.Listing(UUID.randomUUID(),seller,null,5,"item",MarketLedger.State.CANCELLED,i));ledger.compact(1);assertEquals(2,ledger.all().size());assertNotNull(new MarketLedger(temp.resolve("market.yml")).get(active.id()));}
    @Test void unreadableJournalFailsClosed()throws Exception{Path file=temp.resolve("market.yml");Files.writeString(file,"schema: 999\n");assertThrows(Exception.class,()->new MarketLedger(file));Files.writeString(file,"schema: 1\nlistings:\n  broken: [\n");assertThrows(Exception.class,()->new MarketLedger(file));}
}
