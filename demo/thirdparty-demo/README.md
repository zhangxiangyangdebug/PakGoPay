# thirdparty-demo

Mock third-party service for `ThirdPartyBankTransferTestHandler`.

## Run

```bash
cd demo/thirdparty-demo
./run.sh
```

Service port: `8092`

## Endpoints

- `POST /api/v3/deposits`
- `POST /api/v3/transfers`
- `POST /api/v3/deposits/query`
- `POST /api/v3/transfers/query`
- `POST /api/v3/balance`
- `GET /health`

## Built-in merchant credentials

- `mid=24`
  - apiKey: `021fdff9059411f0`
  - signKey: `75b7cb58f2f9fc7cf477172364c4ff39`
- `mid=374`
  - apiKey: `374`
  - signKey: `9a979c9975b056985cd7387604e7e23b`

Authorization header format:

`Authorization: api-key <apiKey>`

Signature algorithm:

- HmacSHA1 + Base64
- Sort params by ASCII key
- Join as `k1=v1&k2=v2...`
- Exclude blank/null and exclude `sign` field

## Docker Deploy

One-click deploy (no args, using built-in defaults):

```bash
cd demo/thirdparty-demo
bash scripts/deploy_thirdparty_demo.sh
```

Override target/options:

```bash
cd demo/thirdparty-demo
bash scripts/deploy_thirdparty_demo.sh --host 8.219.132.103 --user root --port 8092
```

This script follows the same style as backend deploy:

- build jar in maven docker container
- build linux/amd64 image
- copy image tar to server and docker load
- replace running container on target host
