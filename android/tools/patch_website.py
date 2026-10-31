import os
p = r'C:\Users\pc\Desktop\Faiza\maya-ai-assistant\website\index.html'
s = open(p, encoding='utf-8').read()

old_start = s.index("                const res = await fetch(`${API_BASE}/api/admin/generate-license`, {")
old_end = s.index("        function copyKey(key) {")
new = """                const res = await fetch(`${API_BASE}/api/order`, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({
                        plan: currentPlan,
                        name: name,
                        email: email,
                        phone: phone,
                        payment_method: payment,
                        transaction_id: txnId
                    })
                });
                const data = await res.json();

                if (data.success) {
                    localStorage.setItem('faizaOrder', JSON.stringify({ id: data.order_id, email }));
                    resultBox.className = 'result-box success';
                    resultBox.innerHTML = `
                        ✅ অর্ডার পেয়েছি! অর্ডার নম্বর: <strong>${data.order_id}</strong><br>
                        আমরা আপনার পেমেন্ট মিলিয়ে দেখে লাইসেন্স কী পাঠাব (সাধারণত ১–২ ঘণ্টা)।<br>
                        <button class="btn" style="margin-top:10px" onclick="checkOrder()">🔄 স্ট্যাটাস দেখুন</button>
                        <div id="orderStatus" style="margin-top:8px"></div>
                    `;
                } else {
                    resultBox.className = 'result-box error';
                    resultBox.textContent = `❌ ${data.error}`;
                }
            } catch (err) {
                resultBox.className = 'result-box error';
                resultBox.textContent = '⚠️ Cannot connect to server. Please try again later.';
            }
        }

        /** The buyer comes back later: shows "waiting", the key, or why it was rejected. */
        async function checkOrder() {
            let saved = null;
            try { saved = JSON.parse(localStorage.getItem('faizaOrder') || 'null'); } catch (e) {}
            const id = saved?.id || prompt('অর্ডার নম্বর:');
            const email = saved?.email || prompt('যে ইমেইল দিয়েছিলেন:');
            if (!id || !email) return;
            const out = document.getElementById('orderStatus') || document.getElementById('buyResult');
            try {
                const res = await fetch(`${API_BASE}/api/order/${encodeURIComponent(id)}?email=${encodeURIComponent(email)}`);
                const o = await res.json();
                if (!res.ok) { out.textContent = '❌ ' + (o.error || 'Not found'); return; }
                if (o.status === 'approved' && o.license_key) {
                    out.innerHTML = `🎉 আপনার লাইসেন্স কী:<div class="license-key-display" onclick="copyKey('${o.license_key}')" title="Click to copy">${o.license_key}</div><small>📋 চাপ দিয়ে কপি করুন, অ্যাপে Settings → License-এ দিন।</small>`;
                } else if (o.status === 'rejected') {
                    out.textContent = '❌ পেমেন্ট পাওয়া যায়নি: ' + (o.note || '') + ' — সাপোর্টে যোগাযোগ করুন।';
                } else {
                    out.textContent = '⏳ এখনো যাচাই হচ্ছে, একটু পরে আবার দেখুন।';
                }
            } catch (err) { out.textContent = '⚠️ সার্ভারে যোগাযোগ করা যাচ্ছে না।'; }
        }

"""
s = s[:old_start] + new + s[old_end:]
s = s.replace("        const API_BASE = 'http://localhost:5000';",
              "        // Your live server (same as the app's FAIZA_SERVER_URL). Localhost only while testing on the PC.\n"
              "        const API_BASE = (location.protocol.startsWith('http') && location.hostname !== 'localhost' && location.port !== '8777')\n"
              "            ? location.origin : 'http://localhost:5000';", 1)
open(p, 'w', encoding='utf-8').write(s)
print('ok')
