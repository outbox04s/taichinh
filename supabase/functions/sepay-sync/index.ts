import {createClient} from "https://esm.sh/@supabase/supabase-js@2";
import {sha256} from "../_shared/sepay.ts";
const json=(body:Record<string,unknown>,status=200)=>new Response(JSON.stringify(body),{status,headers:{"content-type":"application/json","cache-control":"no-store"}});
const sleep=(ms:number)=>new Promise(resolve=>setTimeout(resolve,ms));
async function fetchWithBackoff(url:string,token:string):Promise<Response>{for(let attempt=0;attempt<3;attempt++){const response=await fetch(url,{headers:{Authorization:`Bearer ${token}`,Accept:"application/json"}});if(response.status!==429)return response;if(attempt<2)await sleep(Math.min(Number(response.headers.get("retry-after")??1)*1000,5000));}throw new Error("SEPAY_RATE_LIMITED");}
Deno.serve(async(request)=>{
 if(request.method!=="POST")return json({error:"method_not_allowed"},405);
 const authorization=request.headers.get("authorization");if(!authorization?.startsWith("Bearer "))return json({error:"unauthorized"},401);
 const url=Deno.env.get("SUPABASE_URL")!,anon=Deno.env.get("SUPABASE_ANON_KEY")!;
 const userClient=createClient(url,anon,{global:{headers:{Authorization:authorization}},auth:{persistSession:false}});const {data:{user},error:authError}=await userClient.auth.getUser();if(authError||!user)return json({error:"unauthorized"},401);
 const token=Deno.env.get("SEPAY_API_TOKEN"),baseUrl=(Deno.env.get("SEPAY_API_BASE_URL")??"https://userapi-sandbox.sepay.vn/v2").replace(/\/$/,"");if(!token)return json({error:"sepay_not_configured"},503);
 let input:{from?:string;to?:string}={};try{input=await request.json();}catch{/* default range */}
 const to=input.to?new Date(input.to):new Date(),from=input.from?new Date(input.from):new Date(to.getTime()-30*86400000);if(!Number.isFinite(from.getTime())||!Number.isFinite(to.getTime())||from>to||to.getTime()-from.getTime()>93*86400000)return json({error:"invalid_range"},400);
 const admin=createClient(url,Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,{auth:{persistSession:false}});const {data:run,error:runError}=await admin.from("sepay_reconciliation_runs").insert({user_id:user.id,range_from:from.toISOString(),range_to:to.toISOString()}).select("id").single();if(runError)return json({error:"cannot_start_reconciliation"},500);
 let fetched=0,inserted=0;
 try{for(let page=1;page<=20;page++){
  const query=new URLSearchParams({transaction_date_from:from.toISOString().slice(0,10),transaction_date_to:to.toISOString().slice(0,10),page:String(page),per_page:"100"});const response=await fetchWithBackoff(`${baseUrl}/transactions?${query}`,token);if(!response.ok)throw new Error(`SEPAY_HTTP_${response.status}`);const envelope=await response.json();const items:Record<string,unknown>[]=Array.isArray(envelope.data)?envelope.data:(envelope.data?.items??envelope.data?.transactions??[]);
  for(const item of items){fetched++;const amountIn=Number(item.amount_in??0),amountOut=Number(item.amount_out??0),transferType=amountIn>0?"in":"out",amount=amountIn>0?amountIn:amountOut;if(!Number.isSafeInteger(amount)||amount<=0)continue;const normalized={id:String(item.id),gateway:String(item.bank_brand_name??""),transactionDate:String(item.transaction_date??"").replace("T"," ").slice(0,19),accountNumber:String(item.bank_account_id??""),content:String(item.transaction_content??""),transferType,transferAmount:amount,referenceCode:String(item.reference_number??"")};const raw=JSON.stringify(normalized);const {data,error}=await admin.rpc("process_sepay_event",{p_event_id:normalized.id,p_payload:normalized,p_payload_hash:await sha256(raw),p_account_identifier:normalized.accountNumber,p_transfer_type:transferType,p_amount:amount,p_transaction_at:item.transaction_date,p_description:normalized.content,p_reference_code:normalized.referenceCode||null});if(error)throw new Error(`DB_${error.code}`);if(!data?.duplicate)inserted++;}
  if(items.length<100)break;await sleep(350);
 }
 await admin.from("sepay_reconciliation_runs").update({status:"completed",finished_at:new Date().toISOString(),fetched_count:fetched,inserted_count:inserted}).eq("id",run.id);return json({status:"completed",fetched,inserted});
 }catch(error){await admin.from("sepay_reconciliation_runs").update({status:"failed",finished_at:new Date().toISOString(),fetched_count:fetched,inserted_count:inserted,error_message:String(error).slice(0,300)}).eq("id",run.id);console.error("sepay_reconciliation_failed",{run_id:run.id,reason:String(error).split(":",1)[0]});return json({error:"reconciliation_failed"},502);}
});
