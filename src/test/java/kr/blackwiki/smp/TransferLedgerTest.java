package kr.blackwiki.smp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TransferLedgerTest {
    @TempDir Path temp;
    @Test void eachDurableTransferPhaseSurvivesRestart()throws Exception{Path file=temp.resolve("transfers.yml");var ledger=new TransferLedger(file);UUID id=UUID.randomUUID(),payer=UUID.randomUUID(),receiver=UUID.randomUUID();for(var phase:TransferLedger.State.values()){ledger.put(new TransferLedger.Transfer(id,payer,receiver,149,phase));var restored=new TransferLedger(file).get(id);assertEquals(phase,restored.state());assertEquals(149,restored.amount());assertEquals(payer,restored.payer());assertEquals(receiver,restored.receiver());}}
    @Test void invalidTransfersAndCorruptSchemaFailClosed()throws Exception{UUID id=UUID.randomUUID();assertThrows(IllegalArgumentException.class,()->new TransferLedger.Transfer(UUID.randomUUID(),id,id,10,TransferLedger.State.PREPARED));assertThrows(IllegalArgumentException.class,()->new TransferLedger.Transfer(UUID.randomUUID(),id,UUID.randomUUID(),0,TransferLedger.State.PREPARED));Path file=temp.resolve("transfers.yml");Files.writeString(file,"schema: 2\n");assertThrows(Exception.class,()->new TransferLedger(file));}
}
