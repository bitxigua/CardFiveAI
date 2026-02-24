const handContainer = document.getElementById('hand-container');
const discardContainer = document.getElementById('discard-container');
const logList = document.getElementById('log-list');
const statusLabel = document.getElementById('status-label');
const handHint = document.getElementById('hand-hint');
const actionMessage = document.getElementById('action-message');
const startBtn = document.getElementById('start-btn');
const huBtn = document.getElementById('hu-btn');
const pengBtn = document.getElementById('peng-btn');
const gangBtn = document.getElementById('gang-btn');
const passBtn = document.getElementById('pass-btn');

startBtn.addEventListener('click', async () => {
    await fetch('/api/game/start', {method: 'POST'});
    await loadState();
});

huBtn.addEventListener('click', () => sendHuDecision(true));
passBtn.addEventListener('click', () => {
    if (currentPending && (currentPending.type === 'SELF_HU' || currentPending.type === 'DISCARD_HU')) {
        sendHuDecision(false);
    } else {
        sendReaction('NONE');
    }
});
pengBtn.addEventListener('click', () => sendReaction('PENG'));
gangBtn.addEventListener('click', () => sendReaction('GANG'));

let pollTimer = null;
let currentPending = null;

async function loadState() {
    const response = await fetch('/api/game/state');
    const state = await response.json();
    render(state);
}

function startPolling() {
    if (pollTimer) {
        return;
    }
    pollTimer = setInterval(loadState, 1200);
}

function render(state) {
    statusLabel.textContent = `${state.status} · ${state.statusMessage}`;
    currentPending = state.pendingRequest;
    renderHand(state.hand, state.pendingRequest);
    renderDiscards(state.discards);
    renderLogs(state.logs);
    renderActions(state.pendingRequest);
}

function renderHand(hand, pending) {
    handContainer.innerHTML = '';
    let hintText = '';
    hand.forEach(tile => {
        const wrapper = document.createElement('div');
        wrapper.className = 'tile-wrapper';
        const canvas = document.createElement('canvas');
        canvas.width = 100;
        canvas.height = 140;
        drawTile(canvas, tile);
        wrapper.appendChild(canvas);

        const label = document.createElement('span');
        label.textContent = `#${tile.index}`;
        wrapper.appendChild(label);

        if (pending && pending.type === 'DISCARD') {
            wrapper.classList.add('clickable');
            wrapper.addEventListener('click', () => sendDiscard(tile.index));
            hintText = pending.message;
        }

        handContainer.appendChild(wrapper);
    });
    handHint.textContent = hintText;
}

function renderDiscards(discards) {
    discardContainer.innerHTML = '';
    discards.forEach(card => {
        const canvas = document.createElement('canvas');
        canvas.width = 80;
        canvas.height = 112;
        drawTile(canvas, card);
        discardContainer.appendChild(canvas);
    });
}

function renderLogs(logs) {
    logList.innerHTML = '';
    logs.slice(-15).forEach(entry => {
        const li = document.createElement('li');
        li.textContent = entry;
        logList.appendChild(li);
    });
}

function renderActions(pending) {
    actionMessage.textContent = pending ? pending.message : '';
    huBtn.disabled = true;
    pengBtn.disabled = true;
    gangBtn.disabled = true;
    passBtn.disabled = true;

    if (!pending) {
        return;
    }

    switch (pending.type) {
        case 'REACTION':
            pengBtn.disabled = !pending.canPeng;
            gangBtn.disabled = !pending.canGang;
            passBtn.disabled = false;
            break;
        case 'SELF_HU':
        case 'DISCARD_HU':
            huBtn.disabled = false;
            passBtn.disabled = false;
            break;
        default:
            break;
    }
}

async function sendDiscard(index) {
    await fetch('/api/game/discard', {
        method: 'POST',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify({index})
    });
    await loadState();
}

async function sendReaction(reaction) {
    await fetch('/api/game/reaction', {
        method: 'POST',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify({reaction})
    });
    await loadState();
}

async function sendHuDecision(hu) {
    await fetch('/api/game/hu', {
        method: 'POST',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify({hu})
    });
    await loadState();
}

startPolling();
loadState();

/* --- Tile rendering --- */
const COLORS = {
    ivory: '#f8f4e8',
    ivoryShade: '#d5ccb5',
    border: '#c1b79f',
    green: '#1b8e4e',
    blue: '#1f6cb3',
    red: '#d6403b',
    yellow: '#f4e5b8'
};

function drawTile(canvas, card) {
    const ctx = canvas.getContext('2d');
    const {width: w, height: h} = canvas;
    drawBackground(ctx, w, h);

    if (card.honorTile) {
        drawHonor(ctx, card.honor, w, h);
    } else if (card.suit === 'TIAO') {
        drawBamboo(ctx, card.rank, w, h);
    } else {
        drawDots(ctx, card.rank, w, h);
    }
}

function drawBackground(ctx, w, h) {
    const radius = Math.min(w, h) * 0.18;
    ctx.clearRect(0, 0, w, h);
    ctx.fillStyle = COLORS.ivory;
    roundedRect(ctx, 0, 0, w, h, radius);
    ctx.fill();

    const grad = ctx.createLinearGradient(0, 0, 0, h);
    grad.addColorStop(0, 'rgba(255,255,255,0.85)');
    grad.addColorStop(0.4, 'rgba(255,255,255,0.2)');
    grad.addColorStop(1, 'rgba(255,255,255,0)');
    ctx.fillStyle = grad;
    roundedRect(ctx, 2, 2, w - 4, h - 4, radius - 2);
    ctx.fill();

    ctx.strokeStyle = COLORS.border;
    ctx.lineWidth = 1.5;
    roundedRect(ctx, 1, 1, w - 2, h - 2, radius - 2);
    ctx.stroke();

    ctx.strokeStyle = 'rgba(0,0,0,0.1)';
    ctx.lineWidth = 3;
    roundedRect(ctx, 3, 3, w - 6, h - 6, radius - 3);
    ctx.stroke();
}

function drawBamboo(ctx, rank, w, h) {
    const available = h - 20;
    const spacing = available / rank;
    const stickW = Math.min(w * 0.22, 18);
    const x = w / 2 - stickW / 2;

    for (let i = 0; i < rank; i++) {
        const y = 10 + i * spacing + (spacing - 10) / 2;
        ctx.fillStyle = COLORS.green;
        roundedRect(ctx, x, y, stickW, spacing - 6, stickW * 0.4);
        ctx.fill();

        ctx.fillStyle = COLORS.yellow;
        ctx.beginPath();
        ctx.arc(w / 2, y + (spacing / 2) - 2, stickW * 0.3, 0, Math.PI * 2);
        ctx.fill();
    }
}

function drawDots(ctx, rank, w, h) {
    const specs = getDotSpecs(rank);
    specs.forEach(spec => {
        const radius = Math.min(w, h) * 0.11 * spec.size;
        const cx = spec.x * w;
        const cy = spec.y * h;

        const grad = ctx.createRadialGradient(
            cx - radius * 0.2, cy - radius * 0.2, radius * 0.2,
            cx, cy, radius
        );
        const baseColor = COLORS[spec.color];
        grad.addColorStop(0, lighten(baseColor, 0.3));
        grad.addColorStop(0.7, baseColor);
        grad.addColorStop(1, shade(baseColor, -0.2));

        ctx.fillStyle = grad;
        ctx.beginPath();
        ctx.arc(cx, cy, radius, 0, Math.PI * 2);
        ctx.fill();

        ctx.strokeStyle = shade(baseColor, -0.3);
        ctx.lineWidth = radius * 0.2;
        ctx.beginPath();
        ctx.arc(cx, cy, radius * 0.8, 0, Math.PI * 2);
        ctx.stroke();

        if (spec.innerRed) {
            ctx.fillStyle = COLORS.red;
            ctx.beginPath();
            ctx.arc(cx, cy, radius * 0.35, 0, Math.PI * 2);
            ctx.fill();
        }
    });
}

function drawHonor(ctx, honor, w, h) {
    ctx.fillStyle = honor === 'HONG_ZHONG' ? COLORS.red :
        honor === 'FA_CAI' ? COLORS.green : COLORS.blue;
    ctx.font = `${h * 0.5}px "STKaiti", "KaiTi", serif`;
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    const map = {
        HONG_ZHONG: '中',
        FA_CAI: '发',
        BAI_BAN: '白'
    };
    ctx.fillText(map[honor] || '', w / 2, h / 2);
}

function getDotSpecs(rank) {
    const left = 0.3;
    const right = 0.7;
    const top = 0.3;
    const bottom = 0.7;
    const middle = 0.5;
    const quarterX = 0.2;
    const threeQuarterX = 0.8;
    const upperBand = 0.28;
    const lowerBand = 0.72;

    const specs = [];
    const addCorner = color => {
        specs.push({x: left, y: top, color, size: 1});
        specs.push({x: right, y: top, color, size: 1});
        specs.push({x: left, y: bottom, color, size: 1});
        specs.push({x: right, y: bottom, color, size: 1});
    };

    switch (rank) {
        case 1:
            specs.push({x: 0.5, y: 0.5, color: 'blue', size: 1.25, innerRed: true});
            break;
        case 2:
            specs.push({x: 0.5, y: top, color: 'green', size: 1});
            specs.push({x: 0.5, y: bottom, color: 'green', size: 1});
            break;
        case 3:
            specs.push({x: left, y: bottom, color: 'green', size: 1});
            specs.push({x: 0.5, y: middle, color: 'green', size: 1});
            specs.push({x: right, y: top, color: 'green', size: 1});
            break;
        case 4:
            addCorner('green');
            break;
        case 5:
            addCorner('green');
            specs.push({x: 0.5, y: middle, color: 'red', size: 1});
            break;
        case 6:
            specs.push({x: 0.4, y: upperBand, color: 'blue', size: 1.05});
            specs.push({x: 0.6, y: upperBand, color: 'blue', size: 1.05});
            specs.push({x: quarterX, y: lowerBand, color: 'green', size: 1});
            specs.push({x: 0.4, y: lowerBand, color: 'green', size: 1});
            specs.push({x: 0.6, y: lowerBand, color: 'green', size: 1});
            specs.push({x: threeQuarterX, y: lowerBand, color: 'green', size: 1});
            break;
        case 7:
            specs.push({x: quarterX, y: lowerBand, color: 'green', size: 1});
            specs.push({x: 0.4, y: lowerBand, color: 'green', size: 1});
            specs.push({x: 0.6, y: lowerBand, color: 'green', size: 1});
            specs.push({x: threeQuarterX, y: lowerBand, color: 'green', size: 1});
            specs.push({x: 0.3, y: 0.4, color: 'blue', size: 1.05});
            specs.push({x: 0.5, y: 0.28, color: 'blue', size: 1.05});
            specs.push({x: 0.7, y: 0.4, color: 'blue', size: 1.05});
            break;
        case 8: {
            const topRow = 0.32;
            const bottomRow = 0.68;
            [quarterX, 0.4, 0.6, threeQuarterX].forEach(x => {
                specs.push({x, y: topRow, color: 'blue', size: 1});
                specs.push({x, y: bottomRow, color: 'green', size: 1});
            });
            break;
        }
        case 9:
            [0.25, 0.5, 0.75].forEach((y, row) => {
                [0.28, 0.5, 0.72].forEach((x, col) => {
                    const center = row === 1 && col === 1;
                    specs.push({
                        x,
                        y,
                        color: center ? 'red' : (row === 1 ? 'blue' : 'green'),
                        size: 1
                    });
                });
            });
            break;
        default:
            break;
    }
    return specs;
}

function roundedRect(ctx, x, y, w, h, r) {
    ctx.beginPath();
    ctx.moveTo(x + r, y);
    ctx.lineTo(x + w - r, y);
    ctx.quadraticCurveTo(x + w, y, x + w, y + r);
    ctx.lineTo(x + w, y + h - r);
    ctx.quadraticCurveTo(x + w, y + h, x + w - r, y + h);
    ctx.lineTo(x + r, y + h);
    ctx.quadraticCurveTo(x, y + h, x, y + h - r);
    ctx.lineTo(x, y + r);
    ctx.quadraticCurveTo(x, y, x + r, y);
}

function lighten(color, amount) {
    const {r, g, b} = hexToRgb(color);
    return `rgb(${Math.min(255, r + 255 * amount)}, ${Math.min(255, g + 255 * amount)}, ${Math.min(255, b + 255 * amount)})`;
}

function shade(color, amount) {
    const {r, g, b} = hexToRgb(color);
    return `rgb(${Math.max(0, r + 255 * amount)}, ${Math.max(0, g + 255 * amount)}, ${Math.max(0, b + 255 * amount)})`;
}

function hexToRgb(hex) {
    const parsed = /^#?([a-f\d]{2})([a-f\d]{2})([a-f\d]{2})$/i.exec(hex);
    if (!parsed) {
        return {r: 0, g: 0, b: 0};
    }
    return {
        r: parseInt(parsed[1], 16),
        g: parseInt(parsed[2], 16),
        b: parseInt(parsed[3], 16)
    };
}
