System.register(["cc"], function (_export, _context) {
  "use strict";

  var _cclegacy, _crd, EPS;

  /**
   * 旋转体：把 (半径, 高度) 侧轮廓绕 Y 轴旋转成连续曲面。
   *
   * 这是本项目最重要的建模原语：头、躯干、耳朵、四肢全部由它生成，
   * 因为轮廓可以任意精细，且法线由轮廓切线解析求出（天然光滑、无接缝）。
   *
   * @param profile 侧轮廓，从下到上 [radius, y]；半径 0 表示收口（自动形成极点）
   * @param segments 圆周分段（建议 28-48，越低越"低多边形"）
   */
  function lathe(profile) {
    var segments = arguments.length > 1 && arguments[1] !== undefined ? arguments[1] : 32;
    var rings = profile.length;
    var positions = [];
    var normals = [];
    var uvs = [];
    var indices = []; // 预先求每层轮廓的 2D 法线：轮廓切线顺时针旋转 90° 指向外侧

    var rimNormal = [];

    for (var i = 0; i < rings; i++) {
      var prev = profile[Math.max(0, i - 1)];
      var next = profile[Math.min(rings - 1, i + 1)];
      var tr = next[0] - prev[0];
      var ty = next[1] - prev[1];
      var len = Math.hypot(tr, ty);

      if (len < EPS) {
        tr = 0;
        ty = 1;
      } else {
        tr /= len;
        ty /= len;
      }

      rimNormal.push([ty, -tr]);
    } // uv：u = 圆周方位（u = θ/2π），v = 归一化高度（0 = 轮廓最低点，1 = 最高点）。
    // v 用高度而不是环索引，是为了让"按 uv 定位的贴花"（如烘焙腮红）能按实际几何位置计算。


    var yMin = profile[0][1];
    var yMax = profile[rings - 1][1];
    var ySpan = Math.abs(yMax - yMin) < EPS ? 1 : yMax - yMin;

    for (var _i = 0; _i < rings; _i++) {
      var [radius, y] = profile[_i];
      var [nr, ny] = rimNormal[_i];
      var v = (y - yMin) / ySpan;

      for (var j = 0; j <= segments; j++) {
        var u = j / segments;
        var theta = u * Math.PI * 2;
        var cos = Math.cos(theta);
        var sin = Math.sin(theta);
        positions.push(radius * cos, y, radius * sin);
        normals.push(nr * cos, ny, nr * sin);
        uvs.push(u, v);
      }
    }

    var stride = segments + 1;

    for (var _i2 = 0; _i2 < rings - 1; _i2++) {
      for (var _j = 0; _j < segments; _j++) {
        var a = _i2 * stride + _j;
        var b = a + 1;
        var c = a + stride;
        var d = c + 1;
        indices.push(a, c, b, b, c, d);
      }
    }

    return {
      positions,
      normals,
      uvs,
      indices
    };
  }
  /**
   * 椭球：三轴半径独立（Q 版头/身体/眼睛的基本体）。
   * 法线按椭球解析式求出（比球面法线更准确，暗部过渡更自然）。
   */


  function ellipsoid(rx, ry, rz) {
    var segments = arguments.length > 3 && arguments[3] !== undefined ? arguments[3] : 32;
    var rings = arguments.length > 4 && arguments[4] !== undefined ? arguments[4] : 22;
    var positions = [];
    var normals = [];
    var uvs = [];
    var indices = [];

    for (var i = 0; i <= rings; i++) {
      var phi = i / rings * Math.PI; // 0(顶) → π(底)

      var sp = Math.sin(phi);
      var cp = Math.cos(phi);

      for (var j = 0; j <= segments; j++) {
        var theta = j / segments * Math.PI * 2;
        var st = Math.sin(theta);
        var ct = Math.cos(theta);
        var x = rx * sp * ct;
        var y = ry * cp;
        var z = rz * sp * st;
        positions.push(x, y, z); // 椭球隐式梯度 (x/rx², y/ry², z/rz²)

        var nx = x / (rx * rx);
        var ny = y / (ry * ry);
        var nz = z / (rz * rz);
        var len = Math.hypot(nx, ny, nz) || 1;
        nx /= len;
        ny /= len;
        nz /= len;
        normals.push(nx, ny, nz);
        uvs.push(j / segments, 1 - i / rings);
      }
    }

    var stride = segments + 1;

    for (var _i3 = 0; _i3 < rings; _i3++) {
      for (var _j2 = 0; _j2 < segments; _j2++) {
        var a = _i3 * stride + _j2;
        var b = a + 1;
        var c = a + stride;
        var d = c + 1;
        indices.push(a, b, c, b, d, c);
      }
    }

    return {
      positions,
      normals,
      uvs,
      indices
    };
  }
  /** 归一化向量 */


  function norm(v) {
    var len = Math.hypot(v[0], v[1], v[2]) || 1;
    return [v[0] / len, v[1] / len, v[2] / len];
  }
  /** 叉乘 */


  function cross(a, b) {
    return [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]];
  }
  /** 扫掠环：截面中心 + 基向量（法线/副法线）+ 椭圆半径（局部坐标系） */


  /**
   * 扫掠管：沿 3D 路径扫掠椭圆截面（耳朵/尾巴/四肢/呆毛）。
   *
   * 使用"平行传输"框架逐节点推进截面，避免路径转弯时截面扭转撕裂；
   * 首尾自动补**半球封头**（Q 版造型里所有末端都必须是圆的，否则从侧后方能看到"空心管口"）。
   *
   * @param nodes 路径节点（位置 + 截面半径 + 可选椭圆缩放）
   * @param radial 圆周分段
   * @param capSteps 封头细分（0 = 不封头；3 已足够圆润）
   */
  function sweep(nodes) {
    var radial = arguments.length > 1 && arguments[1] !== undefined ? arguments[1] : 18;
    var capSteps = arguments.length > 2 && arguments[2] !== undefined ? arguments[2] : 3;
    var count = nodes.length;
    var positions = [];
    var normals = [];
    var uvs = [];
    var indices = []; // 每个节点的切线

    var tangents = [];

    for (var i = 0; i < count; i++) {
      var prev = nodes[Math.max(0, i - 1)].p;
      var next = nodes[Math.min(count - 1, i + 1)].p;
      tangents.push(norm([next[0] - prev[0], next[1] - prev[1], next[2] - prev[2]]));
    } // 平行传输：逐节点推进截面基向量（避免急转弯处扭转撕裂）


    var ref = Math.abs(tangents[0][1]) > 0.92 ? [1, 0, 0] : [0, 1, 0];
    var normal = norm(cross(tangents[0], ref));
    var rings = [];

    for (var _i4 = 0; _i4 < count; _i4++) {
      var t = tangents[_i4];
      var dot = normal[0] * t[0] + normal[1] * t[1] + normal[2] * t[2];
      var n = norm([normal[0] - t[0] * dot, normal[1] - t[1] * dot, normal[2] - t[2] * dot]);

      if (!isFinite(n[0]) || !isFinite(n[1]) || !isFinite(n[2])) {
        n = norm(cross(t, ref));
      }

      normal = n;
      var binormal = norm(cross(t, normal));
      var node = nodes[_i4];
      rings.push({
        c: node.p,
        n: normal,
        b: binormal,
        r: node.r,
        sx: node.sx === undefined ? 1 : node.sx,
        sy: node.sy === undefined ? 1 : node.sy
      });
    } // 半球封头：以端点截面为赤道，沿切线外侧按 cos 收缩到极点


    if (capSteps > 0 && count >= 2) {
      var startCap = [];
      var head = rings[0];
      var dirStart = [-tangents[0][0], -tangents[0][1], -tangents[0][2]];

      for (var k = capSteps; k >= 1; k -= 1) {
        var angle = k / capSteps * (Math.PI / 2);
        var s = Math.sin(angle);
        var c = Math.cos(angle);
        startCap.push({
          c: [head.c[0] + dirStart[0] * head.r * s, head.c[1] + dirStart[1] * head.r * s, head.c[2] + dirStart[2] * head.r * s],
          n: head.n,
          b: head.b,
          r: head.r * c,
          sx: head.sx,
          sy: head.sy
        });
      }

      var endCap = [];
      var last = rings[rings.length - 1];
      var dirEnd = tangents[count - 1];

      for (var _k = 1; _k <= capSteps; _k += 1) {
        var _angle = _k / capSteps * (Math.PI / 2);

        var _s = Math.sin(_angle);

        var _c = Math.cos(_angle);

        endCap.push({
          c: [last.c[0] + dirEnd[0] * last.r * _s, last.c[1] + dirEnd[1] * last.r * _s, last.c[2] + dirEnd[2] * last.r * _s],
          n: last.n,
          b: last.b,
          r: last.r * _c,
          sx: last.sx,
          sy: last.sy
        });
      }

      rings.unshift(...startCap);
      rings.push(...endCap);
    }

    var total = rings.length;

    for (var _i5 = 0; _i5 < total; _i5++) {
      var ring = rings[_i5];
      var v = total > 1 ? _i5 / (total - 1) : 0;

      for (var j = 0; j <= radial; j++) {
        var u = j / radial;
        var theta = u * Math.PI * 2;
        var cos = Math.cos(theta);
        var sin = Math.sin(theta);
        positions.push(ring.c[0] + (ring.n[0] * cos * ring.sx + ring.b[0] * sin * ring.sy) * ring.r, ring.c[1] + (ring.n[1] * cos * ring.sx + ring.b[1] * sin * ring.sy) * ring.r, ring.c[2] + (ring.n[2] * cos * ring.sx + ring.b[2] * sin * ring.sy) * ring.r);
        var nrm = norm([ring.n[0] * cos * ring.sy + ring.b[0] * sin * ring.sx, ring.n[1] * cos * ring.sy + ring.b[1] * sin * ring.sx, ring.n[2] * cos * ring.sy + ring.b[2] * sin * ring.sx]);
        normals.push(nrm[0], nrm[1], nrm[2]);
        uvs.push(u, v);
      }
    }

    var stride = radial + 1;

    for (var _i6 = 0; _i6 < total - 1; _i6++) {
      for (var _j3 = 0; _j3 < radial; _j3++) {
        var a = _i6 * stride + _j3;
        var b = a + 1;

        var _c2 = a + stride;

        var d = _c2 + 1; // 绕序说明：binormal = cross(tangent, normal)，截面 θ 方向与 outward(normal) 相反，
        // 因此顺序需与 lathe 相反，才能保证正面朝外（否则会被背面剔除）。

        indices.push(a, b, _c2, b, d, _c2);
      }
    }

    return {
      positions,
      normals,
      uvs,
      indices
    };
  }
  /** 欧拉角（XYZ 顺序，弧度）→ 3×3 旋转矩阵（行主序） */


  function eulerMatrix(rot) {
    var [rx, ry, rz] = rot;
    var cx = Math.cos(rx),
        sx = Math.sin(rx);
    var cy = Math.cos(ry),
        sy = Math.sin(ry);
    var cz = Math.cos(rz),
        sz = Math.sin(rz);
    return [cy * cz, -cy * sz, sy, sx * sy * cz + cx * sz, -sx * sy * sz + cx * cz, -sx * cy, -cx * sy * cz + sx * sz, cx * sy * sz + sx * cz, cx * cy];
  }
  /** 几何体变换（位置/旋转/缩放；法线用旋转矩阵变换，随后归一化） */


  function transformed(geo, t) {
    var rot = t.rot || [0, 0, 0];
    var scale = t.scale || [1, 1, 1];
    var pos = t.pos || [0, 0, 0];
    var m = eulerMatrix(rot);
    var positions = [];
    var normals = [];
    var count = geo.positions.length / 3;

    for (var i = 0; i < count; i++) {
      var x = geo.positions[i * 3] * scale[0];
      var y = geo.positions[i * 3 + 1] * scale[1];
      var z = geo.positions[i * 3 + 2] * scale[2];
      positions.push(m[0] * x + m[1] * y + m[2] * z + pos[0], m[3] * x + m[4] * y + m[5] * z + pos[1], m[6] * x + m[7] * y + m[8] * z + pos[2]);
      var nx = geo.normals[i * 3];
      var ny = geo.normals[i * 3 + 1];
      var nz = geo.normals[i * 3 + 2];
      var tx = m[0] * nx + m[1] * ny + m[2] * nz;
      var ty = m[3] * nx + m[4] * ny + m[5] * nz;
      var tz = m[6] * nx + m[7] * ny + m[8] * nz;
      var len = Math.hypot(tx, ty, tz) || 1;
      normals.push(tx / len, ty / len, tz / len);
    }

    return {
      positions,
      normals,
      uvs: geo.uvs.slice(),
      indices: geo.indices.slice()
    };
  }
  /** 合并多个几何体为一个（同一材质下减少 draw call；索引自动偏移） */


  function merge() {
    var out = {
      positions: [],
      normals: [],
      uvs: [],
      indices: []
    };
    var base = 0;

    for (var _len = arguments.length, geos = new Array(_len), _key = 0; _key < _len; _key++) {
      geos[_key] = arguments[_key];
    }

    for (var geo of geos) {
      out.positions.push(...geo.positions);
      out.normals.push(...geo.normals);
      out.uvs.push(...geo.uvs);

      for (var index of geo.indices) {
        out.indices.push(index + base);
      }

      base += geo.positions.length / 3;
    }

    return out;
  }
  /**
   * 平滑法线：按位置把重合顶点分组求平均后写回。
   *
   * 用途：把多个部件（如头 + 腮 + 额头）焊接处的法线统一，
   * 消除"两个球相交处出现硬边/亮线"的廉价感。
   */


  function smoothNormals(geo) {
    var precision = arguments.length > 1 && arguments[1] !== undefined ? arguments[1] : 4;
    var map = new Map();
    var count = geo.positions.length / 3;

    var key = i => geo.positions[i * 3].toFixed(precision) + ',' + geo.positions[i * 3 + 1].toFixed(precision) + ',' + geo.positions[i * 3 + 2].toFixed(precision);

    for (var i = 0; i < count; i++) {
      var k = key(i);
      var acc = map.get(k);

      if (acc) {
        acc[0] += geo.normals[i * 3];
        acc[1] += geo.normals[i * 3 + 1];
        acc[2] += geo.normals[i * 3 + 2];
      } else {
        map.set(k, [geo.normals[i * 3], geo.normals[i * 3 + 1], geo.normals[i * 3 + 2]]);
      }
    }

    var normals = geo.normals.slice();

    for (var _i7 = 0; _i7 < count; _i7++) {
      var _acc = map.get(key(_i7));

      var len = Math.hypot(_acc[0], _acc[1], _acc[2]) || 1;
      normals[_i7 * 3] = _acc[0] / len;
      normals[_i7 * 3 + 1] = _acc[1] / len;
      normals[_i7 * 3 + 2] = _acc[2] / len;
    }

    return {
      positions: geo.positions.slice(),
      normals,
      uvs: geo.uvs.slice(),
      indices: geo.indices.slice()
    };
  }
  /**
   * 由"半径-高度"控制点生成顺滑的旋转体轮廓（Catmull-Rom 插值）。
   *
   * 好处：造型只写少量关键点（如头部的额/颊/下巴宽度），
   * 由插值生成几十层轮廓，保证曲面连续（不会出现折角）。
   */

  /**
   * 旋转体轮廓在指定高度的半径（线性插值）。
   *
   * 用途：头部是 lathe 生成的旋转体，五官定位时需要知道"脸在某个高度有多宽"，
   * 据此把眼睛/鼻子/嘴贴到曲面外侧的正确位置。
   */


  function profileAt(profile, y) {
    if (profile.length === 0) {
      return 0;
    }

    if (y <= profile[0][1]) {
      return profile[0][0];
    }

    var last = profile[profile.length - 1];

    if (y >= last[1]) {
      return last[0];
    }

    for (var i = 0; i < profile.length - 1; i++) {
      var a = profile[i];
      var b = profile[i + 1];

      if (y >= a[1] && y <= b[1]) {
        var span = b[1] - a[1];
        var t = span < 1e-6 ? 0 : (y - a[1]) / span;
        return a[0] + (b[0] - a[0]) * t;
      }
    }

    return last[0];
  }
  /**
   * 旋转体前表面深度：给定 (x, y) 求该处表面在前半球的 z。
   *
   * 面部所有部件（眼/鼻/嘴/腮红）都应通过它定位，
   * 保证五官贴合面部曲率（否则会像零件悬浮在脸前）。
   * 超出轮廓范围时返回 0（调用方应保证 x 在脸颊范围内）。
   *
   * @param profile 旋转体侧轮廓（局部坐标，见 lathe）
   * @param x 相对旋转轴的水平偏移
   * @param y 相对旋转体原点的垂直偏移
   * @param zScale 生成几何时 z 方向的缩放系数（默认 1）
   */


  function frontZ(profile, x, y) {
    var zScale = arguments.length > 3 && arguments[3] !== undefined ? arguments[3] : 1;
    var radius = profileAt(profile, y);
    var inner = radius * radius - x * x;
    return inner <= 0 ? 0 : Math.sqrt(inner) * zScale;
  }
  /**
   * 球面片：贴在球面上的"椭圆帽"，用于需要**贴合曲面曲率**的部件
   * （腮红的柔和色斑、贴在眼球表面的高光片）。
   *
   * 顶点方向来自切平面椭圆参数化：dir(t, φ) = normalize(w + u·t·tanU·cosφ + v·t·tanV·sinφ)，
   * t ∈ [0,1] 是到片中心的归一化径向距离，写入 uv.x（柔和 alpha 衰减直接读它）。
   *
   * 用球面片而不是扁平椭球的原因：扁平片贴到凸曲面上时边缘会离开表面（悬浮感），
   * 球面片的曲率与头部/眼球一致，视觉上是"长在脸上"而不是"贴上去"。
   *
   * @param dir 片中心方向（球心指向片中心）
   * @param angleU 水平张角（弧度）
   * @param angleV 垂直张角（弧度）
   * @param radii 椭球三轴半径
   */


  function spherePatch(dir, angleU, angleV, radii) {
    var segments = arguments.length > 4 && arguments[4] !== undefined ? arguments[4] : 26;
    var rings = arguments.length > 5 && arguments[5] !== undefined ? arguments[5] : 5;
    var w = norm(dir);
    var ref = Math.abs(w[1]) > 0.9 ? [1, 0, 0] : [0, 1, 0];
    var u = norm(cross(ref, w));
    var v = cross(w, u);
    var tanU = Math.tan(angleU);
    var tanV = Math.tan(angleV);
    var positions = [];
    var normals = [];
    var uvs = [];
    var indices = [];

    for (var i = 0; i <= rings; i++) {
      var t = i / rings;

      for (var j = 0; j <= segments; j++) {
        var phi = j / segments * Math.PI * 2;
        var dx = Math.cos(phi) * tanU * t;
        var dy = Math.sin(phi) * tanV * t;
        var inv = 1 / Math.sqrt(1 + dx * dx + dy * dy);
        var sx = (w[0] + u[0] * dx + v[0] * dy) * inv;
        var sy = (w[1] + u[1] * dx + v[1] * dy) * inv;
        var sz = (w[2] + u[2] * dx + v[2] * dy) * inv;
        positions.push(sx * radii[0], sy * radii[1], sz * radii[2]); // 椭球法线：逐分量除以半径平方后归一化

        var nx = sx / (radii[0] * radii[0]);
        var ny = sy / (radii[1] * radii[1]);
        var nz = sz / (radii[2] * radii[2]);
        var nl = Math.hypot(nx, ny, nz) || 1;
        normals.push(nx / nl, ny / nl, nz / nl);
        uvs.push(t, phi / (Math.PI * 2));
      }
    }

    var stride = segments + 1;

    for (var _i8 = 0; _i8 < rings; _i8++) {
      for (var _j4 = 0; _j4 < segments; _j4++) {
        var a = _i8 * stride + _j4;
        var b = a + 1;
        var c = a + stride;
        var d = c + 1;
        indices.push(a, c, b, b, c, d);
      }
    }

    return {
      positions,
      normals,
      uvs,
      indices
    };
  }
  /** 把 uv.x 重写为"到原点的归一化距离"（单位球贴花用；柔和 alpha 衰减读取 uv.x） */


  function radialUV(geo) {
    var count = geo.positions.length / 3;
    var uvs = geo.uvs.slice();
    var maxLen = 0;

    for (var i = 0; i < count; i++) {
      var len = Math.hypot(geo.positions[i * 3], geo.positions[i * 3 + 1], geo.positions[i * 3 + 2]);

      if (len > maxLen) {
        maxLen = len;
      }
    }

    var inv = maxLen > 0 ? 1 / maxLen : 1;

    for (var _i9 = 0; _i9 < count; _i9++) {
      uvs[_i9 * 2] = Math.hypot(geo.positions[_i9 * 3], geo.positions[_i9 * 3 + 1], geo.positions[_i9 * 3 + 2]) * inv;
    }

    return {
      positions: geo.positions.slice(),
      normals: geo.normals.slice(),
      uvs,
      indices: geo.indices.slice()
    };
  }

  function profileFrom(control) {
    var steps = arguments.length > 1 && arguments[1] !== undefined ? arguments[1] : 18;

    if (control.length < 2) {
      return control.slice();
    }

    var out = [];

    var at = i => control[Math.max(0, Math.min(control.length - 1, i))];

    for (var i = 0; i < control.length - 1; i++) {
      var p0 = at(i - 1);
      var p1 = at(i);
      var p2 = at(i + 1);
      var p3 = at(i + 2);
      var last = i === control.length - 2;
      var n = last ? steps : steps - 1;

      for (var s = 0; s <= n; s++) {
        var t = s / n;
        var t2 = t * t;
        var t3 = t2 * t;
        var r = 0.5 * (2 * p1[0] + (-p0[0] + p2[0]) * t + (2 * p0[0] - 5 * p1[0] + 4 * p2[0] - p3[0]) * t2 + (-p0[0] + 3 * p1[0] - 3 * p2[0] + p3[0]) * t3);
        var y = 0.5 * (2 * p1[1] + (-p0[1] + p2[1]) * t + (2 * p0[1] - 5 * p1[1] + 4 * p2[1] - p3[1]) * t2 + (-p0[1] + 3 * p1[1] - 3 * p2[1] + p3[1]) * t3);
        out.push([Math.max(0, r), y]);
      }
    }

    return out;
  }

  _export({
    lathe: lathe,
    ellipsoid: ellipsoid,
    sweep: sweep,
    transformed: transformed,
    merge: merge,
    smoothNormals: smoothNormals,
    profileAt: profileAt,
    frontZ: frontZ,
    spherePatch: spherePatch,
    radialUV: radialUV,
    profileFrom: profileFrom
  });

  return {
    setters: [function (_cc) {
      _cclegacy = _cc.cclegacy;
    }],
    execute: function () {
      _crd = true;

      _cclegacy._RF.push({}, "0f82cLOaQ5F858250xrrJWb", "PetMeshFactory", undefined);
      /**
       * 手写网格工厂（视觉重构 v4）—— 用连续曲面替代"球体拼装"。
       *
       * 为什么需要它：
       *  - primitives 只有球/柱/锥等简单体，拼装出的角色剪影破碎、接缝穿插、法线不连续，
       *    这正是上一版"零件感、廉价感"的根本原因；
       *  - 引擎提供 utils.MeshUtils.createMesh(IGeometry)，允许直接提交任意顶点数据，
       *    因此这里用"旋转体放样(lathe) + 扫掠(sweep) + 平滑法线"生成一体化连续曲面。
       *
       * 约定：
       *  - 坐标系为 Cocos 右手系，+Y 向上，+Z 为角色正前方（与 PetModelBuilder 一致）；
       *  - 几何在本地空间生成，由调用方用节点变换定位（与旧版 part/ball 用法保持一致）；
       *  - 输出结构与 primitives.IGeometry 兼容，可直接喂给 createMesh。
       */

      /** 与 primitives.IGeometry 兼容的几何数据（仅使用渲染必需字段） */

      /** 三维点（数组形式，避免在数学层引入引擎依赖） */

      /** 扫掠路径节点：位置 + 截面半径（可分别缩放 x/y 形成椭圆截面） */

      /** 顶点变换参数（位置/欧拉角(弧度)/缩放） */


      EPS = 1e-6;

      _cclegacy._RF.pop();

      _crd = false;
    }
  };
});
//# sourceMappingURL=febec9a40443f00adba4e0e52a4a9c85382f01d7.js.map