import http from 'node:http';

const port = Number(process.env.PORT || 8080);
const key = process.env.ANTHROPIC_API_KEY;
const expectedToken = process.env.VAANI_AUTH_TOKEN;
const MAX_CONTEXT = 24;

function json(res, status, body) { res.writeHead(status, {'content-type':'application/json'}); res.end(JSON.stringify(body)); }
function authorized(req) {
  // Replace this simple server-to-server gate with verified end-user JWT middleware before deployment.
  return Boolean(expectedToken) && req.headers.authorization === `Bearer ${expectedToken}`;
}
function valid(body) {
  const operations = new Set(['REPLY','REWRITE','TRANSLATE','SUMMARIZE','SEARCH','EXTRACT_ACTIONS','VOICE_TO_ACTION']);
  return body && operations.has(body.operation) && typeof body.conversationId === 'string' &&
    typeof body.requesterId === 'string' && Array.isArray(body.messages) && body.messages.length <= MAX_CONTEXT &&
    body.messages.every(m => typeof m.id === 'string' && typeof m.text === 'string' && m.text.length > 0 && m.text.length <= 4000);
}
function system(operation) {
  return `You are VAANI's communication assistant. Operation: ${operation}. Use only supplied messages. Do not claim decisions not present. Mark suggestions as suggestions. Never execute actions; return concise assistance.`;
}
async function assist(body) {
  const response = await fetch('https://api.anthropic.com/v1/messages', {
    method: 'POST',
    headers: {'content-type':'application/json','x-api-key':key,'anthropic-version':'2023-06-01'},
    body: JSON.stringify({model:'claude-3-5-haiku-latest', max_tokens:700, system:system(body.operation), messages:[{role:'user', content:JSON.stringify({prompt:body.prompt || '', targetLanguage:body.targetLanguage || null, messages:body.messages})}]})
  });
  if (!response.ok) throw new Error(`Claude request failed (${response.status})`);
  const payload = await response.json();
  return {text: payload.content?.filter(x => x.type === 'text').map(x => x.text).join('') || '', citations: [], actions: []};
}
http.createServer(async (req,res) => {
  if (req.method === 'GET' && req.url === '/health') return json(res, key && expectedToken ? 200 : 503, {ready:Boolean(key && expectedToken)});
  if (req.method !== 'POST' || req.url !== '/v1/ai/assist') return json(res,404,{error:'not_found'});
  if (!authorized(req)) return json(res,401,{error:'unauthorized'});
  let raw=''; for await (const part of req) { raw += part; if(raw.length > 120000) return json(res,413,{error:'payload_too_large'}); }
  let body; try { body=JSON.parse(raw); } catch { return json(res,400,{error:'invalid_json'}); }
  if (!valid(body)) return json(res,400,{error:'invalid_request'});
  if (!key) return json(res,503,{error:'ai_not_configured'});
  try { return json(res,200,await assist(body)); } catch (error) { console.error(error.message); return json(res,502,{error:'provider_unavailable'}); }
}).listen(port, () => console.log(`VAANI AI backend listening on ${port}`));
