import { Routes } from '@angular/router';
import { authGuard } from './auth.guard';
import { clientShopGuard } from './client-shop.guard';
import { LoginPage } from './login.page';
import { RegisterPage } from './register.page';
import { ShellPage } from './shell.page';
import { DashboardPage } from './dashboard.page';
import { ShopPage } from './shop.page';
import { OrdersPage } from './orders.page';
import { InventoryPage } from './inventory.page';
import { RepairsPage } from './repairs.page';
import { RepairTrackingPage } from './repair-tracking.page';
import { RepairWorkspacePage } from './repair-workspace.page';
import { CompaniesPage } from './companies.page';
export const routes:Routes=[
 {path:'login',component:LoginPage},
 {path:'register',component:RegisterPage},
 {path:'',component:ShellPage,canActivate:[authGuard],children:[
   {path:'',pathMatch:'full',redirectTo:'dashboard'},
   {path:'dashboard',component:DashboardPage},
   {path:'shop',component:ShopPage,canActivate:[clientShopGuard]},
   {path:'orders',component:OrdersPage},
   {path:'inventory',component:InventoryPage},
   {path:'repairs',component:RepairsPage},
   {path:'track-repairs',component:RepairTrackingPage},
   {path:'repair-workspace',component:RepairWorkspacePage},
   {path:'companies',component:CompaniesPage}
 ]},
 {path:'**',redirectTo:''}
];
