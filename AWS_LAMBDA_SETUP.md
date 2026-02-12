# AWS Lambda Deployment Setup Guide

This guide explains how to deploy your Java Compiler application to AWS Lambda alongside your existing Heroku deployment.

## Architecture

Your application now supports **TWO deployment targets**:
1. ✅ **Heroku** - Existing deployment (unchanged)
2. 🆕 **AWS Lambda** - New serverless deployment

Both use the **same codebase** and work independently!

---

## Features

### Why Lambda?
- ⚡ **True serverless** - Scales to zero (no idle costs)
- 🚀 **Fast cold starts** - ~100-200ms with native image
- 💰 **Pay per use** - Free tier: 1M requests/month
- 🌐 **Function URL** - Direct HTTPS access (no API Gateway needed)
- 📦 **Small package** - ~30-40MB native binary

### Lambda vs Heroku
| Feature | Heroku | AWS Lambda |
|---------|--------|------------|
| Cold start | 12-30s (dyno provisioning) | 100-200ms (native) |
| Idle cost | Dyno runs 24/7 ($5-7/mo) | Zero (scales to zero) |
| Pricing | $5-7/month flat | Pay per request (free tier!) |
| Startup time | 20ms | 20ms |
| Best for | Always-on apps | Sporadic traffic |

---

## Prerequisites

### 1. AWS Account Setup

1. **Create AWS Account** (if you don't have one)
   - Go to: https://aws.amazon.com/
   - Sign up for free tier

2. **Create IAM User for GitHub Actions**
   ```bash
   # Create user via AWS Console:
   # IAM → Users → Create User
   # Name: github-actions-lambda-deploy
   # Access: Programmatic access
   ```

3. **Attach Policy to User**
   ```json
   {
     "Version": "2012-10-17",
     "Statement": [
       {
         "Effect": "Allow",
         "Action": [
           "lambda:CreateFunction",
           "lambda:UpdateFunctionCode",
           "lambda:UpdateFunctionConfiguration",
           "lambda:GetFunction",
           "lambda:GetFunctionConfiguration",
           "lambda:PublishVersion",
           "lambda:CreateFunctionUrlConfig",
           "lambda:GetFunctionUrlConfig",
           "lambda:AddPermission",
           "iam:PassRole"
         ],
         "Resource": "*"
       }
     ]
   }
   ```

4. **Create Access Keys**
   - IAM → Users → [your user] → Security credentials
   - Create access key
   - Save `AWS_ACCESS_KEY_ID` and `AWS_SECRET_ACCESS_KEY`

### 2. Create Lambda Execution Role

1. **Go to IAM → Roles → Create Role**
2. **Select**: AWS service → Lambda
3. **Attach policies**:
   - `AWSLambdaBasicExecutionRole` (for CloudWatch logs)
4. **Role name**: `JavaCompilerLambdaRole`
5. **Copy the Role ARN** (e.g., `arn:aws:iam::123456789012:role/JavaCompilerLambdaRole`)

---

## GitHub Secrets Setup

Add these secrets to your GitHub repository:

### Settings → Secrets → Actions → New repository secret

1. **AWS_ACCESS_KEY_ID**
   - Value: Your IAM user access key ID

2. **AWS_SECRET_ACCESS_KEY**
   - Value: Your IAM user secret access key

3. **AWS_LAMBDA_ROLE_ARN**
   - Value: The Lambda execution role ARN
   - Example: `arn:aws:iam::123456789012:role/JavaCompilerLambdaRole`

---

## Configuration

### 1. Update Lambda Function Name (Optional)

Edit `.github/workflows/deploy-lambda.yml`:

```yaml
env:
  AWS_REGION: 'us-east-1'  # Change to your preferred region
  LAMBDA_FUNCTION_NAME: 'JavaCompilerFunction'  # Change to your function name
```

### 2. Deploy to Lambda

**Option A: Automatic (Push to GitHub)**
```bash
git add .
git commit -m "Add AWS Lambda deployment"
git push origin master
```

GitHub Actions will automatically:
1. Build GraalVM native image
2. Create/update Lambda function
3. Set up Function URL
4. Test the endpoint

**Option B: Manual Trigger**
- Go to: GitHub → Actions → "Deploy Native Image to AWS Lambda"
- Click "Run workflow"

---

## Deployment Workflows

Your repository now has **TWO workflows**:

### 1. Heroku Deployment (`deploy-heroku.yml`)
- ✅ **Unchanged** - Works as before
- Triggers: Push to master
- Deploys to: Heroku dyno

### 2. Lambda Deployment (`deploy-lambda.yml`)
- 🆕 **New** - Deploys to AWS Lambda
- Triggers: Push to master (only when src/ or pom.xml changes)
- Deploys to: AWS Lambda with Function URL

**Both workflows run independently!**

---

## Testing Your Lambda Function

### 1. Get Function URL

After deployment, check GitHub Actions output for Function URL:
```
Function URL: https://abc123xyz.lambda-url.us-east-1.on.aws/
```

### 2. Test Endpoints

```bash
# Health check
curl https://YOUR_FUNCTION_URL/health

# Compiler API
curl -X POST https://YOUR_FUNCTION_URL/api/compiler \
  -H "Content-Type: application/json" \
  -d '{
    "sourceCode": "public class Hello { public static void main(String[] args) { System.out.println(\"Hello Lambda!\"); } }"
  }'
```

### 3. View Logs

```bash
# Install AWS CLI first
aws logs tail /aws/lambda/JavaCompilerFunction --follow
```

---

## Local Testing (Optional)

### Build Lambda package locally:
```bash
./mvnw clean package -Pnative \
  -Dquarkus.profile=lambda \
  -Dquarkus.native.container-build=true \
  -DskipTests
```

Output: `target/function.zip` (~30-40MB)

---

## Cost Estimate

### AWS Lambda Free Tier (Forever)
- ✅ 1 million requests per month
- ✅ 400,000 GB-seconds compute time per month

### Example Usage
If your app gets **10,000 requests/month**:
- Requests: 10,000 (well under 1M free tier)
- Compute: ~100ms per request × 512MB = 50 GB-seconds
- **Cost: $0.00** (free tier)

Even **100,000 requests/month** would be free!

### Heroku Comparison
- Heroku Eco: $5/month (always running)
- Lambda: $0/month for low traffic (scales to zero)

**For low-traffic apps: Lambda saves $5-7/month!**

---

## Troubleshooting

### Function not found
```bash
# Check if function exists
aws lambda get-function --function-name JavaCompilerFunction
```

### Function URL not working
```bash
# Get Function URL
aws lambda get-function-url-config --function-name JavaCompilerFunction
```

### Check logs
```bash
# View recent logs
aws logs tail /aws/lambda/JavaCompilerFunction --since 1h
```

### Re-deploy
```bash
# Trigger manual deployment
gh workflow run deploy-lambda.yml
```

---

## Cleanup (If Needed)

### Delete Lambda Function
```bash
aws lambda delete-function --function-name JavaCompilerFunction
```

### Delete Function URL
```bash
aws lambda delete-function-url-config --function-name JavaCompilerFunction
```

---

## Important Notes

1. **Heroku deployment is NOT affected**
   - Heroku workflow (`deploy-heroku.yml`) works as before
   - Lambda extension doesn't change Heroku behavior
   - You can deploy to both simultaneously

2. **Cold starts**
   - First request after idle: ~100-200ms (much faster than Heroku!)
   - Subsequent requests: ~20ms
   - Lambda keeps function warm for ~15 minutes after use

3. **Function URL vs API Gateway**
   - We use **Function URL** (simpler, faster, free)
   - No need for API Gateway
   - Direct HTTPS access to Lambda

4. **Security**
   - Function URL is **public** by default
   - Add IAM auth if needed (edit workflow)
   - Can add CloudFront + WAF for DDoS protection

---

## Next Steps

1. ✅ Set up AWS account and IAM user
2. ✅ Add GitHub secrets (AWS credentials + role ARN)
3. ✅ Push to GitHub (auto-deploys to Lambda)
4. ✅ Test your Function URL
5. ✅ Monitor logs via AWS CloudWatch

Your app now runs on **both Heroku AND AWS Lambda**! 🎉

Choose Lambda for serverless/sporadic traffic, or Heroku for always-on apps.
