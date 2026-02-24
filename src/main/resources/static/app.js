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
    ivory: '#f8f2e7',
    ivoryShade: '#eadcc1',
    border: '#c9b8a1',
    deepShadow: 'rgba(15,16,18,0.35)',
    blue: '#1d6eb3',
    green: '#1e9b6c',
    jade: '#009b6f',
    red: '#d54646',
    amber: '#f3dda3',
    ink: '#0f3f66'
};

const honorImages = {
    HONG_ZHONG: createImageAsset('/assets/honors/hongzhong.png'),
    FA_CAI: createImageAsset('/assets/honors/facai.png'),
    BAI_BAN: createImageAsset('/assets/honors/baiban.png')
};

const tileImages = {
    TIAO_1: createImageAsset('/assets/bamboo/yitiao.jpg'),
    TIAO_2: createImageAsset('/assets/bamboo/ertiao.jpeg'),
    TIAO_3: createImageAsset('/assets/bamboo/santiao.jpeg'),
    TIAO_4: createImageAsset('/assets/bamboo/sitiao.png'),
    TIAO_5: createImageAsset('/assets/bamboo/wutiao.png'),
    TIAO_6: createImageAsset('/assets/bamboo/liutiao.png'),
    TIAO_7: createImageAsset('/assets/bamboo/qitiao.png'),
    TIAO_8: createImageAsset('/assets/bamboo/batiao.png'),
    TIAO_9: createImageAsset('/assets/bamboo/jiutiao.png'),
    TONG_1: createImageAsset('/assets/bamboo/yitong.png'),
    TONG_2: createImageAsset('/assets/bamboo/ertong.png'),
    TONG_3: createImageAsset('/assets/bamboo/santong.png'),
    TONG_4: createImageAsset('/assets/bamboo/sitong.jpeg'),
    TONG_5: createImageAsset('/assets/bamboo/wutong.png'),
    TONG_6: createImageAsset('/assets/bamboo/liutong.png'),
    TONG_7: createImageAsset('/assets/bamboo/qitong.png'),
    TONG_8: createImageAsset('/assets/bamboo/batong.png'),
    TONG_9: createImageAsset('/assets/bamboo/jiutong.png')
};

function createImageAsset(src) {
    const img = new Image();
    img.src = src;
    img.onload = () => loadState();
    return img;
}

function drawTile(canvas, card) {
    const ctx = canvas.getContext('2d');
    const {width: w, height: h} = canvas;
    drawBackground(ctx, w, h);

    if (card.honorTile) {
        if (!drawHonorImage(ctx, card.honor, w, h)) {
            drawHonor(ctx, card.honor, w, h);
        }
    } else if (card.suit === 'TIAO') {
        const key = `TIAO_${card.rank}`;
        if (drawTileSuitImage(ctx, key, w, h)) {
            return;
        }
        drawBamboo(ctx, card.rank, w, h);
    } else if (card.suit === 'TONG') {
        const key = `TONG_${card.rank}`;
        if (drawTileSuitImage(ctx, key, w, h)) {
            return;
        }
        drawDots(ctx, card.rank, w, h);
    } else {
        drawDots(ctx, card.rank, w, h);
    }
}

function drawBackground(ctx, w, h) {
    const radius = Math.min(w, h) * 0.2;
    ctx.clearRect(0, 0, w, h);

    const baseGrad = ctx.createLinearGradient(0, 0, 0, h);
    baseGrad.addColorStop(0, '#fffdf8');
    baseGrad.addColorStop(0.45, COLORS.ivory);
    baseGrad.addColorStop(1, COLORS.ivoryShade);
    ctx.fillStyle = baseGrad;
    roundedRect(ctx, 0, 0, w, h, radius);
    ctx.fill();

    ctx.strokeStyle = COLORS.border;
    ctx.lineWidth = 1.5;
    roundedRect(ctx, 0.8, 0.8, w - 1.6, h - 1.6, radius - 2);
    ctx.stroke();

    const gloss = ctx.createLinearGradient(0, 0, 0, h * 0.65);
    gloss.addColorStop(0, 'rgba(255,255,255,0.85)');
    gloss.addColorStop(1, 'rgba(255,255,255,0)');
    ctx.fillStyle = gloss;
    roundedRect(ctx, 2, 2, w - 4, h * 0.55, radius - 4);
    ctx.fill();

    ctx.save();
    ctx.shadowColor = COLORS.deepShadow;
    ctx.shadowBlur = 12;
    ctx.shadowOffsetY = 4;
    roundedRect(ctx, 3, 6, w - 6, h - 9, radius - 5);
    ctx.strokeStyle = 'rgba(0,0,0,0)';
    ctx.stroke();
    ctx.restore();
}

function drawRingCircle(ctx, cx, cy, outerRadius, colors) {
    ctx.lineWidth = outerRadius * 0.15;
    ctx.strokeStyle = colors.ring;
    ctx.beginPath();
    ctx.arc(cx, cy, outerRadius, 0, Math.PI * 2);
    ctx.stroke();

    ctx.fillStyle = colors.fill;
    ctx.beginPath();
    ctx.arc(cx, cy, outerRadius - ctx.lineWidth, 0, Math.PI * 2);
    ctx.fill();
}

function drawTileSuitImage(ctx, key, w, h) {
    const img = tileImages[key];
    if (!img || !img.complete || !img.naturalWidth) {
        return false;
    }
    const padding = Math.min(w, h) * 0.12;
    ctx.drawImage(img, padding, padding, w - padding * 2, h - padding * 2);
    return true;
}

function drawHonorImage(ctx, honor, w, h) {
    const img = honorImages[honor];
    if (!img || !img.complete || !img.naturalWidth) {
        return false;
    }
    const padding = Math.min(w, h) * 0.18;
    const drawWidth = w - padding * 2;
    const drawHeight = h - padding * 2;
    ctx.drawImage(img, padding, padding, drawWidth, drawHeight);
    return true;
}

function drawBamboo(ctx, rank, w, h) {
    if (rank === 1) {
        drawPhoenixBamboo(ctx, w, h);
        return;
    }
    const stickWidth = w * 0.16;
    const stickHeight = h * 0.7;
    const specs = getStickSpecs(rank);
    specs.forEach(spec => {
        drawStick(ctx, spec.x * w, spec.y * h, stickWidth, stickHeight, spec.color || COLORS.jade);
    });
}

function drawDots(ctx, rank, w, h) {
    const specs = getDotSpecs(rank);
    const baseRadius = Math.min(w, h) * 0.11;
    specs.forEach(spec => {
        const radius = baseRadius * spec.size;
        const cx = spec.x * w;
        const cy = spec.y * h;
        if (spec.type === 'ring') {
            drawRingCircle(ctx, cx, cy, radius * 1.25, {
                ring: COLORS.ink,
                fill: COLORS.blue
            });
            ctx.fillStyle = COLORS.red;
            ctx.beginPath();
            ctx.arc(cx, cy, radius * 0.55, 0, Math.PI * 2);
            ctx.fill();
            return;
        }

        const baseColor = COLORS[spec.color];
        const grad = ctx.createRadialGradient(
                cx - radius * 0.2, cy - radius * 0.2, radius * 0.1,
                cx, cy, radius
        );
        grad.addColorStop(0, lighten(baseColor, 0.35));
        grad.addColorStop(0.65, baseColor);
        grad.addColorStop(1, shade(baseColor, -0.2));

        ctx.fillStyle = grad;
        ctx.beginPath();
        ctx.arc(cx, cy, radius, 0, Math.PI * 2);
        ctx.fill();

        ctx.strokeStyle = shade(baseColor, -0.35);
        ctx.lineWidth = Math.max(2, radius * 0.2);
        ctx.beginPath();
        ctx.arc(cx, cy, radius - ctx.lineWidth / 2, 0, Math.PI * 2);
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
    const specs = [];
    const cols = [0.25, 0.5, 0.75];
    const rows = [0.27, 0.5, 0.73];

    const addCorners = color => {
        specs.push({x: cols[0], y: rows[0], color, size: 1});
        specs.push({x: cols[2], y: rows[0], color, size: 1});
        specs.push({x: cols[0], y: rows[2], color, size: 1});
        specs.push({x: cols[2], y: rows[2], color, size: 1});
    };

    switch (rank) {
        case 1:
            specs.push({x: 0.5, y: 0.5, type: 'ring', size: 1});
            break;
        case 2:
            specs.push({x: 0.5, y: rows[0], color: 'green', size: 1});
            specs.push({x: 0.5, y: rows[2], color: 'green', size: 1});
            break;
        case 3:
            specs.push({x: cols[0], y: rows[2], color: 'green', size: 1});
            specs.push({x: 0.5, y: 0.5, color: 'blue', size: 1});
            specs.push({x: cols[2], y: rows[0], color: 'green', size: 1});
            break;
        case 4:
            addCorners('green');
            break;
        case 5:
            addCorners('green');
            specs.push({x: 0.5, y: 0.5, color: 'red', size: 1});
            break;
        case 6:
            [cols[0], cols[2]].forEach(x => {
                specs.push({x, y: rows[0], color: 'blue', size: 1.05});
                specs.push({x, y: rows[2], color: 'green', size: 1});
            });
            specs.push({x: cols[0], y: 0.5, color: 'green', size: 1});
            specs.push({x: cols[2], y: 0.5, color: 'green', size: 1});
            break;
        case 7:
            [cols[0], cols[1], cols[2]].forEach((x, idx) => {
                specs.push({x, y: rows[2], color: 'green', size: 1});
                if (idx !== 1) {
                    specs.push({x, y: 0.45, color: 'blue', size: 1});
                }
            });
            specs.push({x: 0.5, y: rows[0] - 0.08, color: 'blue', size: 1});
            break;
        case 8:
            cols.forEach(x => {
                specs.push({x, y: rows[0], color: 'blue', size: 1});
                specs.push({x, y: rows[2], color: 'green', size: 1});
            });
            specs.push({x: cols[0], y: 0.5, color: 'blue', size: 1});
            specs.push({x: cols[2], y: 0.5, color: 'green', size: 1});
            break;
        case 9:
            rows.forEach((y, rowIdx) => {
                cols.forEach((x, colIdx) => {
                    const center = rowIdx === 1 && colIdx === 1;
                    specs.push({
                        x,
                        y,
                        color: center ? 'red' : (rowIdx === 1 ? 'blue' : 'green'),
                        size: center ? 1.1 : 1
                    });
                });
            });
            break;
        default:
            break;
    }
    return specs;
}

function getStickSpecs(rank) {
    const specs = [];
    const cols = {left: 0.32, center: 0.5, right: 0.68};
    const rows = {top: 0.28, mid: 0.5, bottom: 0.72};

    const addColumn = (x, positions) => positions.forEach(pos => specs.push({x, y: rows[pos]}));

    switch (rank) {
        case 2:
            addColumn(cols.left, ['mid']);
            addColumn(cols.right, ['mid']);
            break;
        case 3:
            addColumn(cols.left, ['mid']);
            addColumn(cols.center, ['mid']);
            addColumn(cols.right, ['mid']);
            break;
        case 4:
            addColumn(cols.left, ['top', 'bottom']);
            addColumn(cols.right, ['top', 'bottom']);
            break;
        case 5:
            addColumn(cols.left, ['top', 'bottom']);
            addColumn(cols.right, ['top', 'bottom']);
            specs.push({x: cols.center, y: rows.mid});
            break;
        case 6:
            addColumn(cols.left, ['top', 'mid', 'bottom']);
            addColumn(cols.right, ['top', 'mid', 'bottom']);
            break;
        case 7:
            addColumn(cols.left, ['top', 'mid', 'bottom']);
            addColumn(cols.right, ['top', 'mid', 'bottom']);
            specs.push({x: cols.center, y: rows.top - 0.08});
            break;
        case 8:
            addColumn(cols.left, ['top', 'mid', 'bottom']);
            addColumn(cols.right, ['top', 'mid', 'bottom']);
            specs.push({x: cols.center, y: rows.top - 0.05});
            specs.push({x: cols.center, y: rows.bottom + 0.05});
            break;
        case 9:
            addColumn(cols.left, ['top', 'mid', 'bottom']);
            addColumn(cols.center, ['top', 'mid', 'bottom']);
            addColumn(cols.right, ['top', 'mid', 'bottom']);
            break;
        default:
            addColumn(cols.center, ['mid']);
            break;
    }
    return specs;
}

function drawStick(ctx, cx, cy, width, height, color) {
    const x = cx - width / 2;
    const y = cy - height / 2;
    const grad = ctx.createLinearGradient(cx, y, cx, y + height);
    grad.addColorStop(0, lighten(color, 0.3));
    grad.addColorStop(1, shade(color, -0.2));
    ctx.fillStyle = grad;
    roundedRect(ctx, x, y, width, height, width * 0.4);
    ctx.fill();

    ctx.strokeStyle = shade(color, -0.4);
    ctx.lineWidth = 1.2;
    roundedRect(ctx, x, y, width, height, width * 0.4);
    ctx.stroke();

    const nodeRadius = width * 0.35;
    [0.33, 0.67].forEach(ratio => {
        ctx.fillStyle = COLORS.amber;
        ctx.beginPath();
        ctx.arc(cx, y + height * ratio, nodeRadius, 0, Math.PI * 2);
        ctx.fill();
        ctx.strokeStyle = color;
        ctx.lineWidth = 1;
        ctx.stroke();
    });
}

function drawPhoenixBamboo(ctx, w, h) {
    const cx = w / 2;
    const baseY = h * 0.7;
    const grad = ctx.createLinearGradient(cx, h * 0.2, cx, baseY);
    grad.addColorStop(0, lighten(COLORS.jade, 0.25));
    grad.addColorStop(1, COLORS.jade);
    ctx.fillStyle = grad;
    ctx.beginPath();
    ctx.moveTo(cx, h * 0.2);
    ctx.quadraticCurveTo(cx - w * 0.12, baseY - h * 0.1, cx - w * 0.04, baseY);
    ctx.quadraticCurveTo(cx, baseY + h * 0.05, cx + w * 0.04, baseY);
    ctx.quadraticCurveTo(cx + w * 0.12, baseY - h * 0.1, cx, h * 0.2);
    ctx.fill();

    [-1, 1].forEach(dir => {
        ctx.fillStyle = lighten(COLORS.jade, 0.15);
        ctx.beginPath();
        ctx.moveTo(cx, h * 0.25);
        ctx.quadraticCurveTo(cx + dir * w * 0.18, h * 0.35, cx + dir * w * 0.1, h * 0.55);
        ctx.quadraticCurveTo(cx + dir * w * 0.03, h * 0.6, cx, h * 0.4);
        ctx.fill();
    });

    ctx.fillStyle = COLORS.red;
    ctx.beginPath();
    ctx.arc(cx, h * 0.22, w * 0.06, 0, Math.PI * 2);
    ctx.fill();
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
    const {r, g, b} = parseColor(color);
    return `rgb(${Math.min(255, r + 255 * amount)}, ${Math.min(255, g + 255 * amount)}, ${Math.min(255, b + 255 * amount)})`;
}

function shade(color, amount) {
    const {r, g, b} = parseColor(color);
    return `rgb(${Math.max(0, r + 255 * amount)}, ${Math.max(0, g + 255 * amount)}, ${Math.max(0, b + 255 * amount)})`;
}

function parseColor(value) {
    if (value.startsWith('#')) {
        const parsed = /^#?([a-f\d]{2})([a-f\d]{2})([a-f\d]{2})$/i.exec(value);
        if (parsed) {
            return {
                r: parseInt(parsed[1], 16),
                g: parseInt(parsed[2], 16),
                b: parseInt(parsed[3], 16)
            };
        }
    }
    const match = /rgba?\((\d+),\s*(\d+),\s*(\d+)/i.exec(value);
    if (match) {
        return {
            r: parseInt(match[1], 10),
            g: parseInt(match[2], 10),
            b: parseInt(match[3], 10)
        };
    }
    return {r: 0, g: 0, b: 0};
}
