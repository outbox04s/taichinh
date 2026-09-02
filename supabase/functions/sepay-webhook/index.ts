import {createClient} from "https://esm.sh/@supabase/supabase-js@2";
import {MAX_BODY_BYTES,parseWebhookPayload,sePayDateToIso,sha256,verifyHmac} from "../_shared/sepay.ts";
const json=(body:Record<string,unknown>,status:number)=>new Response(JSON.stringify(body),{status,headers:{"content-type":"application/json; charset=utf-8","cache-control":"no-store"}});
Deno.serve(async(request)=>{
 if(request.method!=="POST")return json({success:false,message:"Method not allowed"},405);
 const contentType=request.headers.get("content-type")?.split(";",1)[0].trim().toLowerCase();if(contentType!=="application/json")return json({success:false,message:"Unsupported content type"},415);
 if(Number(request.headers.get("content-length")??0)>MAX_BODY_BYTES)return json({success:false,message:"Payload too large"},413);
 const rawBody=await request.text();if(new TextEncoder().encode(rawBody).byteLength>MAX_BODY_BYTES)return json({success:false,message:"Payload too large"},413);
 const environment=Deno.env.get("SEPAY_ENVIRONMENT")??"production",secret=Deno.env.get("SEPAY_WEBHOOK_SECRET");
 const validHmac=Boolean(secret&&await verifyHmac(secret,request.headers.get("x-sepay-timestamp"),request.headers.get("x-sepay-signature"),rawBody));
 const apiKey=Deno.env.get("SEPAY_WEBHOOK_API_KEY"),validSandboxKey=Boolean(environment==="sandbox"&&apiKey&&request.headers.get("authorization")===`Apikey ${apiKey}`);
 if(!validHmac&&!validSandboxKey)return json({success:false,message:"Unauthorized"},401);
 let payload;try{payload=parseWebhookPayload(rawBody);}catch{return json({success:false,message:"Invalid payload"},400);}
 const admin=createClient(Deno.env.get("SUPABASE_URL")!,Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,{auth:{persistSession:false}});
 const {error}=await admin.rpc("process_sepay_event",{p_event_id:String(payload.id),p_payload:payload,p_payload_hash:await sha256(rawBody),p_account_identifier:payload.accountNumber,p_transfer_type:payload.transferType,p_amount:payload.transferAmount,p_transaction_at:sePayDateToIso(payload.transactionDate),p_description:payload.content,p_reference_code:payload.referenceCode||null});
 if(error){console.error("sepay_webhook_processing_failed",{code:error.code});return json({success:false,message:"Processing failed"},500);}
 return json({success:true},200);
});
