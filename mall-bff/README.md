# mall-bff

Node.js BFF (Backend For Frontend) layer for CloudMart.

## Setup

```bash
cp .env.example .env
npm install
npm run dev
```

## Scripts

| Command         | Description                  |
| --------------- | ---------------------------- |
| `npm run dev`   | Start dev server with HMR    |
| `npm run build` | Compile TypeScript           |
| `npm start`     | Run compiled production build |
| `npm run lint`  | Lint source files            |
| `npm test`      | Run tests                    |

## BFF Endpoints

| Method | Path                          | Description                                       |
| ------ | ----------------------------- | ------------------------------------------------- |
| GET    | /api/bff/home                 | Homepage aggregation (products, promos, live)     |
| GET    | /api/bff/products             | Product list with category info inlined           |
| GET    | /api/bff/products/:id         | Product detail with reviews, inventory, price     |
| GET    | /api/bff/products/search      | Search with ES results                            |
| POST   | /api/bff/orders/checkout      | Create order + payment in one call                |
| GET    | /api/bff/orders/:id           | Order detail with items, payment, shipping        |
| GET    | /api/bff/user/profile         | User profile with recent orders summary           |
| GET    | /api/bff/user/dashboard       | Dashboard: orders, coupons, notifications counts  |
| GET    | /api/bff/cart                 | Cart with product details inlined                 |
| POST   | /api/bff/cart/checkout-preview| Preview checkout with coupon + inventory check    |

## Architecture

All BFF routes forward the `Authorization` header to the API Gateway. Parallel service calls use `Promise.all` for aggregation. If one service call fails, partial data is returned with a `degraded` flag.
