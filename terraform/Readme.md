# Spendings WebApi – Terraform Infrastructure

Infrastruktura Google Cloud Platform (GCP) zarządzana przez Terraform dla serwisu **`spendings-webapi`**.

---

## ⚠️ Krok wymagany przed pierwszym uruchomieniem: Rezerwacja statycznego zewnętrznego adresu IP

Zewnętrzny statyczny adres IP dla maszyny wirtualnej jest pobierany w [`main.tf`](main.tf) jako źródło danych **`data "google_compute_address"`** (tylko do odczytu), dzięki czemu `terraform destroy` nigdy nie zwalnia zarezerwowanego adresu IP.

### Rezerwacja adresu IP przez `gcloud`:
```bash
gcloud compute addresses create spendings-webapi \
  --project=pwujczyk-pt \
  --region=europe-central2 \
  --network-tier=PREMIUM
```

---

## 🔑 Zmienne (`terraform.tfvars`)

Utwórz plik `terraform/terraform.tfvars` (lub skopiuj z `Home.Configuration` za pomocą `./copy-tfvars.sh`):

```hcl
github_pat                   = "ghp_..."
spendings_webapi_github_repo = "ProductivityTools-Spendings/ProductivityTools.Spendings.WebApi"
```

---

## 🛠️ Uruchamianie Terraform

### Alias dla `g3terraform`
```bash
alias terraform="/google/bin/releases/g3terraform/runner_main --base_service_dir=\$(pwd) --tf_label='terraform_1_13_5'"
```

### Podstawowe komendy (w katalogu `terraform/`)
```bash
terraform init
terraform plan
terraform apply
```
