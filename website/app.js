'use strict';
const copy = document.getElementById('copy-upi');
copy.addEventListener('click', async () => {
  const status = document.getElementById('copy-status');
  try {
    await navigator.clipboard.writeText('jaynishad2010@okhdfcbank');
    status.textContent = 'UPI ID copied. A little thank-you, ready to go.';
  } catch {
    status.textContent = 'Copy this UPI ID: jaynishad2010@okhdfcbank';
  }
});
