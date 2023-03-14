package com.urbanairship.util

import com.google.common.base.Strings
import com.urbanairship.BaseTestCase
import org.junit.Assert
import org.junit.Test

internal class FarmHashFingerprint64Test : BaseTestCase() {

    private val testData = mapOf(
    "dXB@tDQ-v5<H]rq2Pcc*s>nC-[Mdy" to 8365906589669344754UL,
    "!@#$%^&*():=-_][\\|/?.,<> " to 11772040268694734364UL,
    "&&3gRU?[^&ok:He[|K:" to 11792583603419566171UL,
    "9JqLl0AW7e69Y.&vMHQ5C" to 2827089714349584095UL,
    "F7479877-4690-4A44-AFC9-8FE987EA512F:some_other_id" to 6862335115798125349UL,
    "hg[F|\$D&hb$,V4OeXHOa" to 11873385450325105043UL,
    "/dWQW6&i7h$1@" to 11452602314494946942UL,
    "2/?98ns)xbzEVL^:wCS$7l3@_g!zP^<D.-bd6" to 9728733090894310797UL,
    "?c^6BkI#-SLw" to 13133570674398037786UL,
    "wE,gHSvhK Jv=KR#(R |!%vctTJ0fx)" to 413905253809041649UL,
    "5C \$WnO2K@:(4#h" to 2463546464490189UL,
    "Ijiq13Mb_Nn]sA^jhM7eZ\\ExAzSJ" to 12345582939087509209UL,
    ")D<l91" to 6440615040757739207UL,
    "mC=6Tz,AYH|&n99(G!6LyG&QfZ=1^:" to 10432240328043398052UL,
    "7.b^/n=oR_w(vLN?c?xN<5t\$p8HY2!s:U" to 2506644862971557451UL,
    "t,SRdW>l=?AH4\\JQ!.A)Wh,O4\\8" to 4614517891525318442UL,
    "K6Pjv<>ad" to 16506019169567922731UL,
    "" to 11160318154034397263UL,
    "Q" to 13816109650407654260UL,
    "bF&d\$MYIhB.Ac=qC" to 17582099205024456557UL,
    "#cDR^sLO" to 328147020574120176UL,
    "NXooOPwHej5=c_V0(47=-)N!vNdd:\$fMs1B" to 5510670329273533446UL,
    "y2=B@rsu:g9bWU" to 2493222351070148393UL,
    "wi=%v]GoIPI6zm[Rrgmq]7J?.|" to 8222623341199956836UL,
    "Sl,xx&O^l@=TQ[QI(TJ^aD*PS3.K]@Mk:e)e" to 12943788826636368730UL,
    "@05Mz\\\\)VhZ\\S&9vVU,egF%sW)IMIGVHE%#I)D|" to 134798502762145076UL,
    "e#p8" to 252499459662674074UL,
    ">EtzDE,xUUZ%!aCvx#vyN(][Q.eRQO2sBZCwFH" to 5047171257402399197UL,
    "ECCD828C-5D7A-4C8B-9A1B-F244747E96C3" to 9693850515132481856UL,
    "D<wQ1DVVpS" to 876767294656777789UL,
    ",=" to 1326014594494455617UL,
    "EsIIjI65<^!j$)V.,!]M]@Q5[$(oyxI_nF" to 4212098974972931626UL,
    "fDVY|(%&aF#3<l>b?1Y Hqt)qY(0%b@VIk#Rlofs" to 1687730506231245221UL,
    "^b2z)XYJ\\95" to 3150268469206098298UL,
    "9>Nleb)=|CR#4=G2&7[HOP" to 10511875379468936029UL,
    "M)(iJ1-nf>5XCc0L?" to 9968500208262240300UL,
    "WW5" to 6316074107149058620UL,
    "ZyzWj:&3hH78.WUCNW4e&Z " to 13218358187761524434UL,
    "P9|0-Xg" to 15614415556471156694UL,
    "n?(o|a[EX|KN-9./=tCVEmN%?<MXe8F<" to 1754206644017466002UL,
    "&QEO\\" to 673322083973757863UL,
    "T#e:),mqALpU]hrJ%f.*|=&r" to 11789374840096016445UL,
    "xi\\PvQpHpM:$5\\Zh^U" to 4169389423472268625UL,
    "!/tU|0cMaw=/-Yg)m_*4UNvwB" to 14890523890635468863UL
    )

    private val crossPlatformCases = mapOf(
        "0376d8dc-a717-425a-9dd2-d4b36bcaddac:65d52a5d-4f88-4a78-97c9-08464d44bbb6" to 14340110469024474010UL,
        "62087079-4f3b-4350-88a9-67667493a48f:a9689ef1-ef7f-45e7-8841-ba0f6cdc6b4d" to 3536341280875387670UL,
        "3d50751d-360f-4c37-a818-0e2d7b83a795:962a1b84-8ecc-44d7-95ff-3ec60215075a" to 6852554232698863320UL,
        "9ecfe0bf-b24b-422f-83e2-e8de9c336493:27abe283-5937-43f0-a4f2-c8ee71f682e8" to 16343172889285518932UL,
        "601cfb3b-b69f-4b88-b52d-e1fc201df11c:29d407e8-bbc9-4b48-b789-e5af84b22810" to 18171507073648632955UL,
        "67b9dcdf-4d8d-42ed-a000-bf90c3b47fa8:e90c7986-0231-4c80-a10d-fc60bdf05ebc" to 6180626819026048726UL,
        "9431edf5-a862-405e-82bd-0f64283304e9:478f73b9-f324-42f6-9d8f-bb0445f11247" to 5342572022420056632UL,
        "5c24c242-4b81-496d-9d56-31f320d20a26:8155f96f-f3b1-4bcd-8a54-378150ea3d03" to 5403761470481847248UL,
        "976bddc7-7b5c-48fa-a285-a10dc2d64009:6a4ed766-017e-4b19-9cb2-0299e97a995b" to 404533724234009115UL,
        "cede4631-b57a-447d-b94b-4c31a71e1f3c:f46f1e00-1e78-4c01-8a89-989106182ecd" to 2662685979233479610UL,
        "dd464de7-2f57-4787-a14b-35bd57dd515d:2da0af42-35b0-423e-99ed-bc5cb5dd7099" to 5656984155782857542UL,
        "3f1d41bc-ac7e-49e2-8f88-30744f0fff4e:8561c5d7-cbb1-4b67-afd0-669e369420b6" to 3506311998853318899UL,
        "c65ef2ae-44b0-4c5e-b37e-57b4fda7ee8e:d4933f58-d257-41ff-b0fa-11aa524d642e" to 14192866033732275238UL,
        "d19964d4-59e3-49d6-8d61-4dfa97e794f7:6cbc589e-3695-4cc6-afe7-4bcdcea01480" to 8310185173796126101UL,
        "813c09f5-a0ae-410d-99c6-7bf7e87b2738:c5d50a64-bacf-4887-b3ac-e53d8cdc555b" to 15599208209427113891UL,
        "a1db3c20-673e-48e4-9967-b49834b6fac6:a3bc58d1-f389-4113-97d1-28d3bf12cbe5" to 1700656031758233133UL,
        "5b431ab8-975e-4207-8550-62da7665a01b:095c1b48-131e-477c-90d4-17894acc1246" to 7441422609642864761UL,
        "92f4a2ca-46d5-4e15-87c5-f7b33497286c:7811c125-2348-47a6-84e6-9343bb12a0f7" to 592674394864765514UL,
        "cbb0399d-a803-4a27-91a0-7732b308278e:f61a366a-7c20-45eb-b40a-bed0b012607c" to 492797389607996305UL,
        "2c333e41-e702-4096-a71f-8c3df488a990:9c1d45d9-439c-490e-99cc-f159ce7010e7" to 764412364649713065UL,
        "optic_acquit:eef25358-6577-4b84-bbcd-82c0f2de80e2" to 2791352902118037828UL,
        "warthog_punts:bbe20d0c-143d-4d1a-8973-182b7d10c7bb" to 7332285015592839891UL,
        "vanilla_hither:70d7d1ce-09da-468c-9864-d0188f70c1fe" to 8273296097385490599UL,
        "crepe_frumps:bbe484c8-af06-4477-863e-35cfdb284f71" to 8795467158546487560UL,
        "clinic_scouts:ec85318f-dd20-4cde-bc81-69bb1e21b12b" to 6650034920187666365UL,
        "trying_gapped:d72565c0-2d7e-4e37-a309-ad47c9c14da9" to 4989233212801864762UL,
        "snuffly_pithy:84811fbf-badd-405a-8564-7f354190943f" to 15791669038156053022UL,
        "graters_fields:37281aec-3848-4ef2-ac7d-e926618865f7" to 9056534536604691350UL,
        "mirrors_dangs:ddb42326-49f6-40e5-b428-b966f6ab4887" to 4084541845741700082UL,
        "expend_raying:b3054772-ed90-4a79-8866-4a8753f93d2d" to 11334098313106439423UL,
        "peewees_autobus:5de6faf8-e039-4b2c-ba37-ccdd0401758e" to 1590885424516612823UL,
        "giant_boozy:9ceecbc5-0372-4a5c-b12f-4a6d696dece9" to 3196424533567189237UL,
        "glazers_zagging:74e3f557-3064-4d99-8809-f6b4c897a710" to 18418949167652646364UL,
        "paces_acuate:4c08c06d-7ddc-4773-8fcb-4833c5a03b36" to 13404925037839805568UL,
        "makes_coiner:108af86f-b273-463c-96ee-9b4c948e92ac" to 13939548535417169537UL,
        "patinas_posted:e8ff9cd9-e335-4b6c-93ff-51aba68951c9" to 15877907202098665149UL,
        "further_agents:4fb082f2-2db8-4cf0-b367-b22ee0e590e1" to 16609400165765915699UL,
        "hubbubs_parked:b97ff960-af53-42a2-b5fe-9b8e248012f9" to 1116732196685121691UL,
        "deeply_outworn:ef48d2be-76ef-465d-a993-b92cf8f958ac" to 16625481623000662505UL,
        "girded_heave:30aebf7f-8a0c-4b89-adbc-b010b0619f94" to 4262921022933957472UL,
    )

    @Test
    fun testKnownOutput() {
        testData.forEach { (key, value) ->
            Assert.assertEquals(
                value.toLong(),
                FarmHashFingerprint64.fingerprint(key)
            )
        }
    }

    @Test
    fun testCrossPlatformCases() {
        crossPlatformCases.forEach { (key, value) ->
            Assert.assertEquals(
                value.toLong(),
                FarmHashFingerprint64.fingerprint(key)
            )
        }
    }

    @Test
    fun testReallySimpleFingerprints() {
        Assert.assertEquals(8581389452482819506L, FarmHashFingerprint64.fingerprint("test"))
        // 32 characters long
        Assert.assertEquals(
            -4196240717365766262L,
            FarmHashFingerprint64.fingerprint(Strings.repeat("test", 8))
        )
        // 256 characters long
        Assert.assertEquals(
            3500507768004279527L,
            FarmHashFingerprint64.fingerprint(Strings.repeat("test", 64))
        )
    }
}
