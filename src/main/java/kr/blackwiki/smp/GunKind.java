package kr.blackwiki.smp;

import org.bukkit.Material;
import java.util.*;

/** Stable product IDs also identify persisted 3.0 pistols; names never authenticate an item. */
enum GunKind {
    PISTOL("small_pistol","리볼버","small_pistol_ammo","소형 권총 탄환","small_pistol_ammo_pack",Material.IRON_NUGGET,6,6,40,10,250,1,6,4,80,100000,500,2500),
    SHOTGUN("double_barrel_shotgun","더블 배럴 샷건","shotgun_shell","12게이지 산탄",null,Material.GUNPOWDER,1,2,30,16,1561,5,24,3,36,250000,1500,0),
    SNIPER("heavy_sniper","묵직한 저격총","sniper_round","12게이지 산탄","sniper_round_pack",Material.GUNPOWDER,12,1,50,50,1561,1,10,8,192,200000,1500,18000);
    final String id,name,ammoId,ammoName,packId;
    final Material ammoMaterial;
    final int packSize,capacity,reloadTicks,fireTicks,durability,pellets;
    final double damage,speed,range;
    final long price,ammoPrice,packPrice;
    GunKind(String id,String name,String ammoId,String ammoName,String packId,Material ammoMaterial,int packSize,int capacity,int reloadTicks,int fireTicks,int durability,int pellets,double damage,double speed,double range,long price,long ammoPrice,long packPrice){
        this.id=id;this.name=name;this.ammoId=ammoId;this.ammoName=ammoName;this.packId=packId;this.ammoMaterial=ammoMaterial;this.packSize=packSize;this.capacity=capacity;this.reloadTicks=reloadTicks;this.fireTicks=fireTicks;this.durability=durability;this.pellets=pellets;this.damage=damage;this.speed=speed;this.range=range;this.price=price;this.ammoPrice=ammoPrice;this.packPrice=packPrice;
    }
    double seconds(){return reloadTicks/20.0;}
    /** Sniper now shares 12-gauge shells with the shotgun. */
    GunKind ammunition(){return this==SNIPER?SHOTGUN:this;}
    static GunKind gun(String id){return Arrays.stream(values()).filter(k->k.id.equals(id)).findFirst().orElse(null);}
    static GunKind ammo(String id){return Arrays.stream(values()).filter(k->k.ammoId.equals(id)||Objects.equals(k.packId,id)&&id!=null).findFirst().orElse(null);}
}
